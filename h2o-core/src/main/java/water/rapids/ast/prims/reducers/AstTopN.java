package water.rapids.ast.prims.reducers;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

public class AstTopN extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"frame", "col", "nPercent", "getTopN"};
  }

  @Override
  public String str() {
    return "topn";
  }


  @Override
  public int nargs() {
    return -1;
  }

  @Override
  public String example() {
    return "(topn frame col nPercent getBottomN)";
  }

  @Override
  public String description() {
    return "Return the top N percent rows for a numerical column as a frame with two columns.  The first column " +
            "will contain the original row indices of the chosen values.  The second column contains the top N row" +
            "values.  If getBottomN is 1, we will return the bottom N percent.  If getBottomN is 0, we will return" +
            "the top N percent of rows";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    Frame frOriginal = stk.track(asts[1].exec(env)).getFrame(); // get the 2nd argument and convert it to a Frame
    int colIndex = (int) stk.track(asts[2].exec(env)).getNum();     // column index of interest
    double nPercent = stk.track(asts[3].exec(env)).getNum();        //  top or bottom percentage of row to return
    int getBottomN = (int) stk.track(asts[4].exec(env)).getNum();   // 0, return top, 1 return bottom percentage
    int totColumns = frOriginal.numCols();

    // check for valid input parameter values
    assert ((nPercent>0) && (nPercent<=100)); // make sure percent is between 0 and 100.
    assert ((colIndex >=0) && (colIndex < totColumns)); // valid column index specification
    assert ((getBottomN ==0) || (getBottomN==1));       // must be 0 or 1
    assert frOriginal.vec(colIndex).isNumeric();        // make sure we are dealing with numerical column only

    String[] finalColumnNames = {"Row Indices", frOriginal.name(colIndex)}; // set output frame names
    return new ValFrame(frOriginal);

  }
}
