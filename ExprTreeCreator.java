import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.RuntimeException;

/// First, generate dependencies for each stream or relational query
/// , i.e., for each query print out the streams written to and read from
/// Second, use this dependency table to build an expression tree
public class ExprTreeCreator extends perf_queryBaseListener {
  /// The token type for identifiers. This is required to check if a given token
  /// is an identifier or not.
  private int id_ttype_;

  /// A reference to the symbol table created by the SymbolTableCreator pass
  private HashMap<String, IdentifierType> symbol_table_;

  /// The dependency table mapping from identifiers to operations, populated
  /// by this pass.
  private HashMap<String, Operation> dep_table_ = new HashMap<String, Operation>();

  /// The last identifier assigned so far. Used to build an expression tree.
  private String last_assigned_id_ = "";

  /// Constructor
  public ExprTreeCreator(int identifier_ttype, HashMap<String, IdentifierType> symbol_table) {
    id_ttype_ = identifier_ttype;
    symbol_table_ = symbol_table;
  }

  @Override public void exitStream_stmt(perf_queryParser.Stream_stmtContext ctx) {
    perf_queryParser.StreamContext stream = ctx.stream();

    perf_queryParser.Stream_queryContext query = ctx.stream_query();

    Operation operation = getOperation(query);
    for (int i = 0; i < operation.operands.size(); i++) {
      assert(symbol_table_.get(operation.operands.get(i)) == IdentifierType.STREAM);
    }
    dep_table_.put(stream.getText(), operation);
    last_assigned_id_ = stream.getText();
  }

  @Override public void exitProg(perf_queryParser.ProgContext ctx) {
    System.out.println("dep_table_: " + dep_table_);
    System.out.println("expr_tree : " + build_expr_tree(last_assigned_id_));
    System.err.println(build_expr_tree(last_assigned_id_).dot_output());
  }

  /// Get operands and operator that are required for the given query
  private Operation getOperation(perf_queryParser.Stream_queryContext query) {
    assert(query.getChildCount() == 1);
    ParseTree op = query.getChild(0);
    if (op instanceof perf_queryParser.FilterContext) {
      perf_queryParser.FilterContext filter = (perf_queryParser.FilterContext)op;
      return new Operation(OperationType.FILTER, Utility.getAllTokens(filter.stream(), id_ttype_));
    } else if (op instanceof perf_queryParser.GroupbyContext) {
      perf_queryParser.GroupbyContext groupby = (perf_queryParser.GroupbyContext)op;
      return new Operation(OperationType.GROUPBY, Utility.getAllTokens(groupby.stream(), id_ttype_));
    } else if (op instanceof perf_queryParser.MapContext) {
      perf_queryParser.MapContext map = (perf_queryParser.MapContext)op;
      return new Operation(OperationType.PROJECT, Utility.getAllTokens(map.stream(), id_ttype_));
    } else if (op instanceof perf_queryParser.ZipContext) {
      perf_queryParser.ZipContext zip = (perf_queryParser.ZipContext)op;
      ArrayList<perf_queryParser.StreamContext> stream_list = new ArrayList<>(zip.stream());
      ArrayList<String> ret = Utility.getAllTokens(stream_list.get(0), id_ttype_);
      ret.addAll(Utility.getAllTokens(stream_list.get(1), id_ttype_));
      return new Operation(OperationType.JOIN, ret);
    } else {
      assert(false);
      return new Operation();
    }
  }

  private ExprTree build_expr_tree(String id_name) {
   if (id_name.equals("T")) {
     return new ExprTree(OperationType.PKTLOG);
   } else {
     // Get operands using dep_table_
     Operation operation = dep_table_.get(id_name);
     if (operation == null) {
       throw new RuntimeException(id_name + " doesn't exist in dependency table. It was likely not defined before use.");
     }

     // Recursively build_expr_tree for each operand
     ArrayList<ExprTree> children = new ArrayList<ExprTree>();
     assert(operation.operands.size() >= 1);
     for (int i = 0; i < operation.operands.size(); i++) {
       children.add(build_expr_tree(operation.operands.get(i)));
     }
     return new ExprTree(operation.opcode, children);
   }
  }
}
