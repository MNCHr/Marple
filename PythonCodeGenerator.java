import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.misc.Interval;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.RuntimeException;

public class PythonCodeGenerator extends perf_queryBaseListener {
  /// Reference to parser to get underlying token stream.
  /// Required to preserve spaces and retrieve them when unparsing productions
  private perf_queryParser parser_;

  /// A reference to the symbol table created by the SymbolTableCreator pass
  private HashMap<String, IdentifierType> symbol_table_;

  /// Build up function calls string
  private String function_calls_ = "";

  /// Build up function definitions string
  private String function_defs_ = "";

  public PythonCodeGenerator(perf_queryParser t_parser, HashMap<String, IdentifierType> t_symbol_table) {
    parser_ = t_parser;
    symbol_table_ = t_symbol_table;
  }

  /// Use this to print declarations at the beginning of Python code
  /// For tuples and state
  @Override public void enterProg(perf_queryParser.ProgContext ctx) {
    System.err.println(generate_state_class());
    System.err.println(generate_tuple_class());
  }

  /// Use this to print the packet loop that tests the given sql program
  /// at the end of the Python code
  @Override public void exitProg(perf_queryParser.ProgContext ctx) {
    System.err.println(function_defs_);
    System.err.println("# main loop of function calls");
    System.err.println(function_calls_);
  }

  /// Turn aggregation function into a Python function definitoon
  @Override public void exitAgg_fun(perf_queryParser.Agg_funContext ctx) {
    System.err.println("def " + ctx.getChild(1).getText() + " (state, tuple_var):\n");
    System.err.println(generate_state_preamble());
    System.err.println(generate_tuple_preamble());
    System.err.println("  " + text_with_spaces((ParserRuleContext)(ctx.getChild(8))) + "\n");
    System.err.println(generate_state_postamble());
    System.err.println(generate_tuple_postamble());
  }

  /// Turn selects into a Python function definition
  private String filter_def(ParseTree query, ParseTree stream) {
    ParserRuleContext predicate = (ParserRuleContext)(query.getChild(0).getChild(5));
    return (spg_query_signature(stream) +
            generate_tuple_preamble() + "\n" +
            "  valid = " + text_with_spaces(predicate) + "\n\n" +
            generate_tuple_postamble());
  }

  /// Turn SQL projections into Python function definitions
  private String project_def(ParseTree query, ParseTree stream) {
    ParserRuleContext expr_list = (ParserRuleContext)(query.getChild(0).getChild(1));
    ParserRuleContext col_list  = (ParserRuleContext)(query.getChild(0).getChild(5));
    return (spg_query_signature(stream) +
            generate_tuple_preamble() + "\n" +
            "  " + text_with_spaces(col_list) + " = " + text_with_spaces(expr_list) + ";\n\n" +
            generate_tuple_postamble());
  } 

  /// Turn SQL joins into Python function definitions
  private String join_def(ParseTree query, ParseTree stream) {  
    return (join_query_signature(stream) +
            "  ret_tuple = Tuple();\n" +
            "  ret_tuple.valid = tuple1.valid and tuple2.valid\n" +
            "  return ret;\n");
  }

  /// Turn SQL GROUPBYs into Python function definitions
  private String groupby_def(ParseTree query, ParseTree stream) {
    ParserRuleContext groupby_list = (ParserRuleContext)(query.getChild(0).getChild(5));
    ParserRuleContext agg_func     = (ParserRuleContext)(query.getChild(0).getChild(1));
    return (spg_query_signature(stream) +
            "  global state_dict;\n\n" +
            generate_tuple_preamble() + "\n" +
            "  tuple_state = state_dict(" + text_with_spaces(groupby_list) + ");\n\n" +
            "  return " + text_with_spaces(agg_func) + "(tuple_state, tuple_var);" + "\n");
  }

  @Override public void exitStream_stmt(perf_queryParser.Stream_stmtContext ctx) {
    ParseTree stream = ctx.getChild(0);
    assert(stream instanceof perf_queryParser.StreamContext);

    ParseTree query = ctx.getChild(2);
    assert(query instanceof perf_queryParser.Stream_queryContext);

    OperationType operation = getOperationType((perf_queryParser.Stream_queryContext)query);
    if (operation == OperationType.FILTER) {
      function_defs_  += filter_def(query, stream);
      function_calls_ += generate_spg_queries(query, stream);
    } else if (operation == OperationType.PROJECT) {
      function_defs_  += project_def(query, stream);
      function_calls_ += generate_spg_queries(query, stream); 
    } else if (operation == OperationType.JOIN) {
      function_defs_  += join_def(query, stream);
      function_calls_ += generate_join_queries(query, stream); 
    } else if (operation == OperationType.SFOLD) {
      function_defs_  += groupby_def(query, stream);
      function_calls_ += generate_spg_queries(query, stream);
    } else {
      assert(false);
    }
  }

  @Override public void exitRelational_stmt(perf_queryParser.Relational_stmtContext ctx) {
    ParseTree stream = ctx.getChild(0);
    assert(stream instanceof perf_queryParser.RelationContext);

    ParseTree query = ctx.getChild(2);
    assert(query instanceof perf_queryParser.Relational_queryContext);

    OperationType operation = getOperationType((perf_queryParser.Relational_queryContext)query);
    if (operation == OperationType.RFOLD) {
      function_defs_  += groupby_def(query, stream);
      function_calls_ += generate_spg_queries(query, stream);
    } else {
      assert(false);
    }
  }

  /// Signature for Python function for select, project, and group by
  private String spg_query_signature(ParseTree stream) {
    String stream_name = text_with_spaces((ParserRuleContext)stream);
    return "def func" + stream_name + "(tuple_var):\n";
  }

  /// Signature for Python functions for join
  private String join_query_signature(ParseTree stream) {
    String stream_name = text_with_spaces((ParserRuleContext)stream);
    return "def func" + stream_name + "(tuple1, tuple2):\n";
  }

  /// Generate Python function calls for select, project, and group by queries
  private String generate_spg_queries(ParseTree query, ParseTree stream) {
    String stream_name = text_with_spaces((ParserRuleContext)stream);
    String arg_name    = text_with_spaces((ParserRuleContext)query.getChild(0).getChild(3));
    return stream_name + " = func" + stream_name + "(" + arg_name + ")\n";
  }

  /// Generate Python function call for join queries
  private String generate_join_queries(ParseTree query, ParseTree stream) {
    String stream_name = text_with_spaces((ParserRuleContext)stream);
    String arg1   = text_with_spaces((ParserRuleContext)query.getChild(0).getChild(0));
    String arg2   = text_with_spaces((ParserRuleContext)query.getChild(0).getChild(2));
    return stream_name + " = func" + stream_name + "(" + arg1 + ", " + arg2 + ")\n";
  }

  private String text_with_spaces(ParserRuleContext production) {
    Token start_token = production.getStart();
    Token stop_token  = production.getStop();
    return parser_.getTokenStream().getText(start_token, stop_token);
  }

  private String generate_tuple_class() {
    String ret = "# tuple class\n";
    ret += "class Tuple: \n";
    ret += "  def __init__(self):\n";
    for (String key : symbol_table_.keySet()) {
      if (symbol_table_.get(key) == IdentifierType.COLUMN) {
        ret = ret + "    " + "self." + key + " = 0;\n";
      }
    }
    ret += "    self.state = State();\n";
    ret += "    self.valid = False;\n";
    return ret;
  }

  private String generate_tuple_preamble() {
    String ret = "  # tuple preamble\n";
    for (String key : symbol_table_.keySet()) {
      if (symbol_table_.get(key) == IdentifierType.COLUMN) {
        ret = ret + "  " + key + " = tuple_var." + key + "\n";
      }
    }
    return ret;
  }

  private String generate_tuple_postamble() {
    String ret = "  # tuple postamble\n";
    for (String key : symbol_table_.keySet()) {
      if (symbol_table_.get(key) == IdentifierType.COLUMN) {
        ret = ret + "  " + "tuple_var." + key + " = " + key + "\n";
      }
    }
    return ret + "\n" + "  return tuple_var;\n";
  }

  private String generate_state_class() {
    String ret = "# state class\n";
    ret += "class State: \n";
    ret += "  def __init__(self):\n";
    for (String key : symbol_table_.keySet()) {
      if (symbol_table_.get(key) == IdentifierType.STATE) {
        ret = ret + "    " + "self." + key + " = 0;\n";
      }
    }
    ret += "    valid = False;\n";
    return ret;
  }

  private String generate_state_preamble() {
    String ret = "  # state preamble\n";
    for (String key : symbol_table_.keySet()) {
      if (symbol_table_.get(key) == IdentifierType.STATE) {
        ret = ret + "  " + key + " = state." + key + "\n";
      }
    }
    return ret;
  }

  private String generate_state_postamble() {
    String ret = "  # state postamble\n";
    for (String key : symbol_table_.keySet()) {
      if (symbol_table_.get(key) == IdentifierType.STATE) {
        ret = ret + "  " + "state." + key + " = " + key + "\n";
      }
    }
    return ret;
  }

  /// Get operation type for the given query
  /// TODO: This duplicates a lot of code with ExprTreeCreator
  private OperationType getOperationType(ParserRuleContext query) {
    assert(query instanceof perf_queryParser.Stream_queryContext ||
           query instanceof perf_queryParser.Relational_queryContext);
    assert(query.getChildCount() == 1);
    ParseTree op = query.getChild(0);
    if (op instanceof perf_queryParser.FilterContext) {
      // SELECT * FROM stream, so stream is at location 3
      return OperationType.FILTER;
    } else if (op instanceof perf_queryParser.SfoldContext) {
      // SELECT agg_func FROM stream SGROUPBY ...
      return OperationType.SFOLD;
    } else if (op instanceof perf_queryParser.ProjectContext) {
      // SELECT expr_list FROM stream
      return OperationType.PROJECT;
    } else if (op instanceof perf_queryParser.JoinContext) {
      // stream JOIN stream
      return OperationType.JOIN;
    } else if (op instanceof perf_queryParser.RfoldContext) {
      return OperationType.RFOLD;
    } else {
      assert(false);
      return OperationType.UNDEFINED;
    }
  }
}
