package water.rapids.ast.prims.reducers;

import water.Key;
import water.MRTask;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValRow;


public class AstTopN extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"frame", "columnInfo", "nPercent", "getTopN"};
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
    return "(topn frame columnInfo nPercent getTopN na_rm)";
  }

  @Override
  public String description() {
    return "Return the top N percent rows for a numerical column as a frame with two columns.  The first column " +
            "will contain the original row indices of the chosen values.  The second column contains the top N row" +
            "values.  If getTopN is set to be false, we will return the bottom N percent.   Flag na_rm controls " +
            "treatment of " + "the NA values: if it is 1, then NAs are ignored; if it is 0, the NA rows are included " +
            "are included as part of the top N or bottom N percents and they will appear at the front of the sorted" +
            " frame that is returned as a result of this function call.";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    Frame frOriginal = stk.track(asts[1].exec(env)).getFrame(); // get the original frame
    Val val1 = stk.track(asts[1].exec(env));
    if (val1 instanceof ValFrame) {
      Frame fr = stk.track(val1).getFrame();
      boolean na_rm = asts[2].exec(env).getNum() == 1;
      boolean axis = asts.length == 4 && (asts[3].exec(env).getNum() == 1);
      return axis? rowwiseSum(fr, na_rm) : colwisesum(fr, na_rm);
    }
    else if (val1 instanceof ValRow) {
      // This may be called from AstApply when doing per-row computations.
      double[] row = val1.getRow();
      boolean na_rm = asts[2].exec(env).getNum() == 1;
      double d = 0;
      int n = 0;
      for (double r: row) {
        if (Double.isNaN(r)) {
          if (!na_rm)
            return new ValFrame(frOriginal);
           // return new ValRow(new double[]{Double.NaN}, null);
        } else {
          d += r;
          n++;
        }
      }
      //return new ValRow(new double[]{d}, null);
      return new ValFrame(frOriginal);
    } else
      throw new IllegalArgumentException("Incorrect argument to (sum): expected a frame or a row, received " + val1.getClass());
  }


  /**
   * Compute Frame sum for each row. This returns a frame consisting of a single Vec of sums in each row.
   */
  private ValFrame rowwiseSum(Frame fr, final boolean na_rm) {
    String[] newnames = {"sum"};
    Key<Frame> newkey = Key.make();

    // Determine how many columns of different types we have
    int n_numeric = 0, n_time = 0;
    for (Vec vec : fr.vecs()) {
      if (vec.isNumeric()) n_numeric++;
      if (vec.isTime()) n_time++;
    }
    // Compute the type of the resulting column: if all columns are TIME then the result is also time; otherwise
    // if at least one column is numeric then the result is also numeric.
    byte resType = n_numeric > 0? Vec.T_NUM : Vec.T_TIME;

    // Construct the frame over which the sum should be computed
    Frame compFrame = new Frame();
    for (int i = 0; i < fr.numCols(); i++) {
      Vec vec = fr.vec(i);
      if (n_numeric > 0? vec.isNumeric() : vec.isTime())
        compFrame.add(fr.name(i), vec);
    }
    Vec anyvec = compFrame.anyVec();

    //Certain corner cases
    if (anyvec == null) {
      Frame res = new Frame(newkey);
      anyvec = fr.anyVec();
      if (anyvec != null) {
        // All columns in the original frame are non-numeric? Return a vec of NAs
        res.add("sum", anyvec.makeCon(Double.NaN));
      } // else the original frame is empty, in which case we return an empty frame too
      return new ValFrame(res);
    }
    if (!na_rm && n_numeric < fr.numCols() && n_time < fr.numCols()) {
      // If some of the columns are non-numeric and na_rm==false, then the result is a vec of NAs
      Frame res = new Frame(newkey, newnames, new Vec[]{anyvec.makeCon(Double.NaN)});
      return new ValFrame(res);
    }

    // Compute the sum over all rows
    final int numCols = compFrame.numCols();
    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk nc) {
        for (int i = 0; i < cs[0]._len; i++) {
          double d = 0;
          int numNaColumns = 0;
          for (int j = 0; j < numCols; j++) {
            double val = cs[j].atd(i);
            if (Double.isNaN(val))
              numNaColumns++;
            else
              d += val;
          }
          if (na_rm? numNaColumns < numCols : numNaColumns == 0)
            nc.addNum(d);
          else
            nc.addNum(Double.NaN);
        }
      }
    }.doAll(1, resType, compFrame)
            .outputFrame(newkey, newnames, null);

    // Return the result
    return new ValFrame(res);
  }


  /**
   * Compute column-wise sums and return a frame having a single row.
   */
  private ValFrame colwisesum(Frame fr, final boolean na_rm) {
    Frame res = new Frame();

    Vec vec1 = Vec.makeCon(null, 0);
    assert vec1.length() == 1;

    for (int i = 0; i < fr.numCols(); i++) {
      Vec v = fr.vec(i);
      boolean valid = (v.isNumeric() || v.isTime() || v.isBinary()) && v.length() > 0 && (na_rm || v.naCnt() == 0);
      Vec newvec = vec1.makeCon(valid? v.mean() * (v.length() - v.naCnt()) : Double.NaN, v.isTime()? Vec.T_TIME : Vec.T_NUM);
      res.add(fr.name(i), newvec);
    }

    vec1.remove();
    return new ValFrame(res);
  }
}
