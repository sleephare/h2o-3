package water.rapids.ast.prims.reducers;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.ArrayUtils;

import static org.junit.Assert.assertEquals;


/**
 * Test the AstTopN.java class
 */
public class AstTopNTest extends TestUtil {
    @BeforeClass public static void setup() {   // randomly generate a frame here.
        stall_till_cloudsize(1);
    }

    //--------------------------------------------------------------------------------------------------------------------
    // Tests
    //--------------------------------------------------------------------------------------------------------------------
    /** Test written by Nidhi to test that NaOmit actaully remove the rows with NAs in them. */
    @Test public void TestTopN() {
        Frame f = null;
        Frame fNew = null;
        try {
            f = ArrayUtils.frame(ar("A", "B"), ard(1.0, Double.NaN), ard(2.0, 23.3),
                    ard(3.0, 3.3), ard(Double.NaN, 3.2), ard(34.3, 2.3), ard(2.1, 4.4),
                    ard(9.9, -1.3), ard(11.2, 2.34), ard(9.123, 2.321), ard(10.2, 87.2),
                    ard(1.123, 2.421), ard(10.21, 2.432));
            String x = "(topn "+ f._key+ " 1 20 0)";
            Val res = Rapids.exec(x);         // make the call the remove NAs in frame
            fNew = res.getFrame();            // get frame without any NAs
            assertEquals(f.numRows()-fNew.numRows() ,2);  // 2 rows of NAs removed.
        } finally {
            if (f != null) f.delete();
            if (fNew != null) fNew.delete();
        }
    }

}