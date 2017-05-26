package water.rapids.ast.prims.reducers;

import water.MRTask;
import water.fvec.C8Chunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

import java.util.*;

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
    long numRows = Math.round(nPercent*0.01*frOriginal.numRows()); // number of rows to return

    // check for valid input parameter values
    assert (numRows >= 0);
    assert ((nPercent>0) && (nPercent<=100)); // make sure percent is between 0 and 100.
    assert ((colIndex >=0) && (colIndex < totColumns)); // valid column index specification
    assert ((getBottomN ==0) || (getBottomN==1));       // must be 0 or 1
    assert frOriginal.vec(colIndex).isNumeric();        // make sure we are dealing with numerical column only

    String[] finalColumnNames = {"Row Indices", frOriginal.name(colIndex)}; // set output frame names
    GrabTopN grabTask = new GrabTopN(finalColumnNames, numRows, (getBottomN==0));
    grabTask.doAll(frOriginal.vec(colIndex));
    return new ValFrame(frOriginal);

  }

  /*
   Here is the plan:
   1. For each chunk, read in a sorted portion of the chunk into an binary heap with key and value.  The key will
   be the original row number and the value will be the value of course
   2. Inside reduce, make sure you combine the heaps and then sort them back to one into one
   3. Inside the postGlobal, copy the heap key values into a frame and return.
   */
// E depends on column type: long or other numerics
  public  class GrabTopN<E extends Comparable<E>> extends MRTask<GrabTopN<E>>  {
    final String[] _columnName;   // name of column that we are grabbing top N for
    TreeMap _sortHeap;
    Frame _sortedOut;   // store the final result of sorting
    final long _rowSize;   // number of top or bottom rows to keep
    final boolean _increasing;  // sort with Top values first if true.


    private GrabTopN(String[] columnName, long rowSize, boolean increasing) {
      _columnName = columnName;
      _rowSize = rowSize;
      _increasing = increasing;
    }

    @Override public void map(Chunk cs) {
      _sortHeap = new TreeMap();
      Long startRow = cs.start();           // absolute row offset

      for (int rowIndex = 0; rowIndex < cs._len; rowIndex++) {  // stuff our chunks into hashmap
        long absRowIndex = rowIndex+startRow;
        if (!cs.isNA(rowIndex)) { // skip NAN values
          addOneValue(cs, rowIndex, absRowIndex, _sortHeap);
        }
      }

      // reduce heap size to about rowSize
      if (_sortHeap.size() > _rowSize) {  // chop down heap size to around _rowSize
        reduceHeapSize(_sortHeap, _rowSize);
      }
    }

    public void reduceHeapSize(TreeMap tmap, long desiredSize) {
      long numDelete = tmap.size()-desiredSize;
      for (long index=0; index<numDelete; index++) {
        if (_increasing)
          tmap.remove(tmap.firstKey());
        else
          tmap.remove(tmap.lastKey());
      }
    }

    public void addOneValue(Chunk cs, int rowIndex, long absRowIndex, TreeMap sortHeap) {
      if (cs instanceof C8Chunk) {  // long chunk
        long a = cs.at8(rowIndex);
        if (sortHeap.containsKey(a)) {
          ArrayList<Long> allRows = (ArrayList<Long>) sortHeap.get(a);
          allRows.add(absRowIndex);
          sortHeap.put(a, allRows);
        } else {
          ArrayList<Long> allRows = new ArrayList<Long>();
          allRows.add(absRowIndex);
          sortHeap.put(a, allRows);
        }
      } else {                      // other numeric chunk
        double a = cs.atd(rowIndex);
        if (sortHeap.containsKey(a)) {
          ArrayList<Long> allRows = (ArrayList<Long>) sortHeap.get(a);
          allRows.add(absRowIndex);
          sortHeap.put(a, allRows);
        } else {
          ArrayList<Long> allRows = new ArrayList<Long>();
          allRows.add(absRowIndex);
          sortHeap.put(a, allRows);
        }
      }
    }

    @Override public void reduce(GrabTopN<E> other) {
      this._sortHeap.putAll(other._sortHeap);

      if (this._sortHeap.size() > _rowSize) {
        reduceHeapSize(this._sortHeap, _rowSize); // shrink the heap size again.
      }
    }

    @Override public void postGlobal() {  // copy the sorted heap into a vector and make a frame out of it.
      long rowCount = 0l; // count number of rows extracted from Heap and setting to final frame
      
      while (rowCount < _rowSize) {
        if (_increasing) {  // grab the last one

        } else {  // grab the first one

        }
      }
    }
  }

}
