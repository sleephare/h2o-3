package water.rapids.ast.prims.reducers;

import hex.SplitFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.Rapids;
import water.rapids.Val;

/**
 * Test the AstTopN.java class
 */
public class AstTopNTest extends TestUtil {
    static Frame _train;    // store training data
    static Frame _answerTop;        // store top 20% of data in _train
    static Frame _answerBottom;     // store bottom 20% of data in _train

    @BeforeClass public static void setup() {   // randomly generate a frame here.
        stall_till_cloudsize(1);
    }

    //--------------------------------------------------------------------------------------------------------------------
    // Tests
    //--------------------------------------------------------------------------------------------------------------------
    /** Loading in a dataset containing data from -1000000 to 1000000 multiplied by 1.1 as the float column in column 1.
     * The other column (column 0) is a long data type with maximum data value at 2^63. */
    @Test public void TestTopBottomN() {
        Scope.enter();
        int[] checkPercent = {2,3};//2};//, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        int testPercent = 0;      // store test percentage

        // load in the datasets with the answers
        _train = parse_test_file(Key.make("topbottom"), "smalldata/jira/TopBottomN.csv.zip");
        _answerTop = parse_test_file(Key.make("top20"), "smalldata/jira/Top20Per.csv.zip");
        _answerBottom = parse_test_file(Key.make("bottom20"), "smalldata/jira/Bottom20Per.csv.zip");

        Scope.track(_train);
        Scope.track(_answerBottom);
        Scope.track(_answerTop);

        try {
            for (int index = 0; index < checkPercent.length; index++) {
                testPercent = checkPercent[index];
                testTopBottom(_answerTop, testPercent, 0);  // test top %
             //   testTopBottom(_answerBottom, testPercent, 1); // test bottom %
            }
        } finally {
            Scope.exit();
        }
    }

    public static void testTopBottom(Frame answerF, int testPercent, int getBottom) {
        Scope.enter();
        Frame topBN = null;
        Frame topBL = null;
        Frame topBNF = null;
        Frame topBF = null;
        Frame answerFN = null;
        Frame answerLN = null;
        try {
            String x = "(topn " + _train._key + " 0 " + testPercent + " " + getBottom + ")";
            Val res = Rapids.exec(x);         // make the call to grab top/bottom N percent
            topBN = res.getFrame();            // get frame that contains top N elements
            Scope.track(topBN);
            answerLN = answerF.extractFrame(0,1);
            Scope.track(answerLN);
            topBL = topBN.extractFrame(1,2);
            Scope.track(topBL);
            checkTopBottomN(answerLN, topBL, testPercent);

/*            x = "(topn " + _train._key + " 1 " + testPercent + " 0)";       // get top % float
            res = Rapids.exec(x);         // make the call to grab top/bottom N percent
            topBNF = res.getFrame();// get frame that contains top N elements
            topBF = topBNF.extractFrame(1, 2);
            answerFN = answerF.extractFrame(1, 2);
            checkTopBottomN(answerFN, topBF, testPercent);
            Scope.track(topBF);
            Scope.track(answerFN);
            Scope.track(topBNF);*/
        } finally {
            Scope.exit();
        }
    }
    /*
    Helper function to compare test frame result with correct answer
     */
    public static void checkTopBottomN(Frame answerF, Frame grabF, int nPercent) {
        Scope.enter();
        try {
            double tolerance = 1e-12;
            double nfrac = nPercent * 0.01;   // translate percentage to actual fraction
            SplitFrame sf = new SplitFrame(answerF, new double[]{nfrac, 1 - nfrac}, new Key[]{
                    Key.make("topN.hex"), Key.make("bottomN.hex")});
            // Invoke the job
            sf.exec().get();
            Key[] ksplits = sf._destination_frames;
            Frame topN = DKV.get(ksplits[0]).get();
            isIdenticalUpToRelTolerance(topN, grabF, tolerance);
            Scope.track(topN);
            Scope.track_generic(ksplits[1].get());
        } finally {
            Scope.exit();
        }
    }
}