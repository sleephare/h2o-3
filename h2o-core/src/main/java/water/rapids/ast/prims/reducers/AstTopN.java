package water.rapids.ast.prims.reducers;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

import java.util.TreeMap;

public class AstTopN extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"frame", "col", "nPercent", "getBottomN"};
  }

  @Override
  public String str() {
    return "topn";
  }


  @Override
  public int nargs() {
    return 1+4;
  } // function name plus 4 arguments.

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

  /*
   Here is the plan:
   1. For each chunk, read in a sorted portion of the chunk into an binary heap with key and value.  The key will
   be the original row number and the value will be the value of course
   2. Inside reduce, make sure you combine the heaps and then sort them back to one into one
   3. Inside the postGlobal, copy the heap key values into a frame and return.
   */
// E depends on column type: double, int or long
  public static class GrabTopN<E> extends MRTask<GrabTopN<E>>  {
    final String _columnName;   // name of column that we are grabbing top N for
    TreeMap<E, Long> _sortHeap;
    Frame _sortedOut;   // store the final result of sorting
    final int _rowSize;   // number of top or bottom rows to keep


    private GrabTopN(String columnName, int rowSize) {
      _columnName = columnName;
      _rowSize = rowSize;
    }

    @Override public void map(Chunk cs) {

    }

    @Override public void reduce(GrabTopN<E> other) {

    }

    @Override public void postGlobal() {  // copy the sorted heap into a vector and make a frame out of it.

    }

    class TopNComparator 
  }

}
