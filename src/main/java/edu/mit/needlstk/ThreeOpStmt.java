package edu.mit.needlstk;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.HashSet;
import java.util.Arrays;

/// Template for three operand instructions
/// These are statements of the form:
/// result = pred ? expr_if : expr_else
public class ThreeOpStmt {
  private enum StmtType {
    TERNARY,
    PRED_ASSIGN,
    EXPR_ASSIGN,
    EMIT
  };
  private StmtType type;
  private String result;
  private String predVar;
  private AugPred pred;
  private AugExpr expr;
  private AugExpr exprIf;
  private AugExpr exprElse;

  /// Constructor with a predefined predicate variable
  /// result = predVar ? exprIf : exprElse
  public ThreeOpStmt(String result,
                     String predVar,
                     AugExpr exprIf,
                     AugExpr exprElse) {
    this.type = StmtType.TERNARY;
    this.result = result;
    this.predVar = predVar;
    this.exprIf = exprIf;
    this.exprElse = exprElse;
  }

  /// Constructor that defines a predicate condition
  /// result = pred
  public ThreeOpStmt(String result,
                     AugPred pred) {
    this.type = StmtType.PRED_ASSIGN;
    this.result = result;
    this.pred = pred;
  }

  /// Constructor that defines an expression
  /// result = expr
  public ThreeOpStmt(String result,
                     AugExpr expr) {
    this.type = StmtType.EXPR_ASSIGN;
    this.result = result;
    this.expr = expr;
  }

  /// Constructor that emits internal state when a predicate is true.
  public ThreeOpStmt(String predVar) {
    this.type = StmtType.EMIT;
    this.predVar = predVar;
  }

  /// Get list of identifiers used in this statement
  public HashSet<String> getUsedVars() {
    if(type == StmtType.TERNARY) {
      HashSet<String> usedVars = new HashSet<>(Arrays.asList(predVar));
      usedVars.addAll(exprIf.getUsedVars());
      usedVars.addAll(exprElse.getUsedVars());
      return usedVars;
    } else if(type == StmtType.PRED_ASSIGN) {
      return pred.getUsedVars();
    } else if(type == StmtType.EXPR_ASSIGN) {
      return expr.getUsedVars();
    } else if(type == StmtType.EMIT) {
      return new HashSet<>();
    } else {
      assert(false); // Expecting a new statement type?
      return null;
    }
  }

  public String getDefinedVar() {
    if (type == StmtType.TERNARY || type == StmtType.PRED_ASSIGN || type == StmtType.EXPR_ASSIGN) {
      return result;
    } else if (type == StmtType.EMIT) {
      return null;
    } else {
      assert(false); // Expecting a new statement type?
      return null;
    }
  }

  /// Printing for visual inspection on console
  public String print() {
    String res;
    if(type == StmtType.TERNARY) {
      res = (result + " = " + predVar + " ? ("
             + exprIf.print() + ") : ("
             + exprElse.print() + ");");
    } else if(type == StmtType.PRED_ASSIGN) {
      res = result + " = " + pred.print() + ";";
    } else if(type == StmtType.EXPR_ASSIGN) {
      res = result + " = " + expr.print() + ";";
    } else if(type == StmtType.EMIT) {
      res = "if (" + predVar + ") emit;";
    } else {
      assert(false); // Logic error. Expecting a new statement type?
      res = "";
    }
    return res;
  }

  @Override public String toString() {
    return print();
  }

  public boolean isEmit() {
    return (type == StmtType.EMIT);
  }

  public String getEmitPred() {
    assert (type == StmtType.EMIT);
    return predVar;
  }
}
