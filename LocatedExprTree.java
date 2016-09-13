import java.util.ArrayList;

class LocatedExprTree extends ExprTree {
  private OpLocation opl;
  private ArrayList<LocatedExprTree> locOperands;

  public LocatedExprTree(OperationType tOpcode, OpLocation tOpl,
      		   ArrayList<LocatedExprTree> tOperands) {
    super(tOpcode);
    opl = tOpl;
    locOperands = tOperands;
  }

  public LocatedExprTree(OperationType tOpcode, OpLocation tOpl) {
    super(tOpcode);
    opl = tOpl;
    locOperands = new ArrayList<LocatedExprTree>();
  }

  public OpLocation opl() {
    return opl;
  }

  @Override public String toString() {
    if (locOperands.size() == 0) return opcode.toString();

    String ret = opcode + "(";
    for (int i = 0; i < locOperands.size(); i++) {
      ret += locOperands.get(i).toString() + ",";
    }
    ret = ret.substring(0, ret.length() - 1) + ")";
    return ret;
  }

  public String nodeLabel() {
    return opcode + Integer.toString(uid) + " ";
  }

  public String edgeLabel() {
    return opl.toConciseString();
  }

  @Override public String dotEdges() {
    String ret = "";
    if (locOperands.size() != 0) {
      for (int i = 0; i < locOperands.size(); i++) {
      ret += nodeLabel() + " -> " + locOperands.get(i).nodeLabel()
          + " [label=\"" + locOperands.get(i).edgeLabel() + "\"];\n"
          + locOperands.get(i).dotEdges();
      }
    }
    return ret;
  }
}