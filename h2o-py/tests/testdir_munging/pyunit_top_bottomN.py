from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_top_bottomN():
    """
    PUBDEV-3624 Top or Bottom N test h2o.frame.H2OFrame.topN() and h2o.frame.H2OFrame.bottomN() functions.
    Given a H2O frame, a column index or column name, a integer denoting top/bottom rows or a double less than
    1 will denote a fraction of the rows to return, the topN will return a H2OFrame containing two columns, one will
    be the topN (or bottomN) values of the specified column.  The other column will record the row indices into
    the original frame of where the topN (bottomN) values come from.  This will let the users to grab those
    corresponding rows to do whatever they want with it.
    """
    python_lists = np.random.uniform(-1,1, (30,40))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    nP = 10
    newframe = h2oframe.topN(0, nP)
    assert_is_type(newframe, H2OFrame)
    assert newframe.ncols==2, "Top N returned incorrect number of columns"
    assert newframe.nrows==round(nP*0.01*h2oframe.nrows), "Top N returned incorrect top N% rows."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_top_bottomN)
else:
    h2o_H2OFrame_top_bottomN()


