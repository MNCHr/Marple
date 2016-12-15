package edu.mit.needlstk;
import org.antlr.v4.runtime.ParserRuleContext;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;

public class FoldConfigInfo implements PipeConfigInfo {
  private String fnName;
  private List<String> keyFields;
  private List<String> stateArgs;
  private List<String> fieldArgs;
  private List<ThreeOpStmt> code;
  private String outerPredId;
  private HashSet<String> setFields;
  private HashSet<String> usedFields;
  
  public FoldConfigInfo(PerfQueryParser.ColumnListContext colList,
                        String aggFunc,
                        ThreeOpCode code,
                        List<String> stateArgs,
                        List<String> fieldArgs) {
    this.keyFields = ColumnExtractor.getColumns(colList);
    this.stateArgs = stateArgs;
    this.fieldArgs = fieldArgs;
    this.fnName = aggFunc;
    this.code = code.getStmts();
    this.outerPredId = code.peekIdFirstDecl();
    this.setFields = new HashSet<String>(stateArgs);
    this.setFields.addAll(this.keyFields);
    this.usedFields = new HashSet<String>(fieldArgs);
    this.usedFields.addAll(this.keyFields);
  }

  public String getP4() {
    String res = "";
    for (ThreeOpStmt stmt: code) {
      res += stmt.print();
      res += "\n";
    }
    return res;
  }

  public List<ThreeOpStmt> getCode() {
    return code;
  }

  public void addValidStmt(String queryId, String operandQueryId, boolean isOperandPktLog) {
    /// TODO: The code in the aggregation function will execute regardless of the validity of the
    /// operand, resulting in dead code. This needs to be cleaned up.
    ArrayList<ThreeOpStmt> newCode = new ArrayList<ThreeOpStmt>();
    AugPred operandPred = isOperandPktLog ? (new AugPred(true)) : (new AugPred(operandQueryId));
    newCode.add(new ThreeOpStmt(queryId, new AugPred(false)));
    for (ThreeOpStmt line: code) {
      /// Replace the "outermost predicate" of the function to operandPred.
      if (line.isPredAssign()) {
        if (line.getDefinedVar().equals(this.outerPredId)) {
          /// If this line assigns the outermost predicate, we should modify it.
          newCode.add(new ThreeOpStmt(this.outerPredId, operandPred));
        } else {
          /// Add the line unmodified otherwise.
          newCode.add(line);
        }
      } else if (line.isEmit()) {
        // The result from this stage is valid if two conditions are met.
        // (1) The predicate corresponding to the emit is true;
        // (2) the operand is valid.
        AugPred thisBranch = new AugPred(line.getEmitPred());
        newCode.add(new ThreeOpStmt(queryId, thisBranch.or(new AugPred(queryId))));
        newCode.add(line);
      } else {
        /// Add the line unmodified otherwise.
        newCode.add(line);
      }
    }
    code = newCode;
  }

  public HashSet<String> getSetFields() {
    return setFields;
  }

  public HashSet<String> getUsedFields() {
    return usedFields;
  }
}
