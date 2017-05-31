# -*- encoding: utf-8 -*-
import h2o
from h2o.job import H2OJob
from h2o.frame import H2OFrame
from h2o.utils.typechecks import assert_is_type, is_type

class H2OAutoML(object):
    """
    Automatic Machine Learning

    The Automatic Machine Learning (AutoML) function automates the supervised machine learning model training process.
    The current version of AutoML trains and cross-validates a Random Forest, an Extremely-Randomized Forest,
    a random grid of Gradient Boosting Machines (GBMs), a random grid of Deep Neural Nets,
    and a Stacked Ensemble of all the models.

    :examples:
    >>> # Setting up an H2OAutoML object
    >>> project_name = "Project1"
    >>> aml = H2OAutoML(max_runtime_secs=30, project_name=project_name)
    """
    def __init__(self,
                 max_runtime_secs=None,
                 max_models=None,
                 stopping_metric=None,
                 stopping_tolerance=None,
                 stopping_rounds=None,
                 seed=None,
                 project_name=None):

        #Check if H2O jar contains AutoML
        try:
            h2o.api("GET /3/Metadata/schemas/AutoMLV99")
        except h2o.exceptions.H2OResponseError as e:
            print(e)
            print("*******************************************************************\n" \
                  "*Please verify that your H2O jar has the proper AutoML extensions.*\n" \
                  "*******************************************************************\n" \
                  "\nVerbose Error Message:")

        #If max_runtime_secs is not provided, then it is set to default (600 secs)
        if max_runtime_secs is None:
            max_runtime_secs = 600
            self.max_runtime_secs = max_runtime_secs
        else:
            assert_is_type(max_runtime_secs,int)
            self.max_runtime_secs = max_runtime_secs

        #Make bare minimum build_control
        self.build_control = {
            'stopping_criteria': {
                'max_runtime_secs': self.max_runtime_secs,
            }
        }

        #Add other parameters to build_control if available
        if max_models is not None:
            assert_is_type(max_models,int)
            self.build_control["stopping_criteria"]["max_models"] = max_models
            self.max_models = max_models

        if stopping_metric is not None:
            assert_is_type(stopping_metric,str)
            self.build_control["stopping_criteria"]["stopping_metric"] = stopping_metric
            self.stopping_metric = stopping_metric

        if stopping_tolerance is not None:
            assert_is_type(stopping_tolerance,float)
            self.build_control["stopping_criteria"]["stopping_tolerance"] = stopping_tolerance
            self.stopping_tolerence = stopping_tolerance

        if stopping_rounds is not None:
            assert_is_type(stopping_rounds,int)
            self.build_control["stopping_criteria"]["stopping_rounds"] = stopping_rounds
            self.stopping_rounds = stopping_rounds

        if seed is not None:
            assert_is_type(seed,int)
            self.build_control["stopping_criteria"]["seed"] = seed
            self.seed = seed

        #Set project name if provided. If None, then we set in .train() to "automl_" + training_frame.frame_id
        if project_name is not None:
            assert_is_type(project_name,str)
            self.build_control["project_name"] = project_name
            self.project_name = project_name
        else:
            self.project_name = None

        self._job = None
        self._automl_key = None
        self._leader_id = None
        self._leaderboard = None

    #---------------------------------------------------------------------------
    # Basic properties
    #---------------------------------------------------------------------------
    @property
    def leader(self):
        """
        Retrieve the top model from an H2OAutoML object

        :return: an H2O model

        :examples:
        >>> # Set up an H2OAutoML object
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> # Launch H2OAutoML
        >>> aml.train(y=y, training_frame=training_frame)
        >>> # Get the top model
        >>> aml.leader
        """
        return h2o.get_model(self._leader_id)

    @property
    def leaderboard(self):
        """
        Retrieve the leaderboard from an H2OAutoML object

        :return: an H2OFrame with model ids in the first column and evaluation metric in the second column sorted
                 by the evaluation metric

        :examples:
        >>> # Set up an H2OAutoML object
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> # Launch H2OAutoML
        >>> aml.train(y=y, training_frame=training_frame)
        >>> # Get the leaderboard
        >>> aml.leaderboard
        """
        return self._leaderboard

    #---------------------------------------------------------------------------
    # Training AutoML
    #---------------------------------------------------------------------------
    def train(self, x = None, y = None, training_frame = None, fold_column = None, 
              weights_column = None, validation_frame = None, leaderboard_frame=None):
        """
        Begins an AutoML task, a background task that automatically builds a number of models
        with various algorithms and tracks their performance in a leaderboard. At any point 
        in the process you may use H2O's performance or prediction functions on the resulting 
        models.

        :param x: A list of column names or indices indicating the predictor columns.
        :param y: An index or a column name indicating the response column.
        :param fold_column: The name or index of the column in training_frame that holds per-row fold
            assignments.
        :param weights_column: The name or index of the column in training_frame that holds per-row weights.
        :param training_frame: The H2OFrame having the columns indicated by x and y (as well as any
            additional columns specified by fold, offset, and weights).
        :param validation_frame: H2OFrame with validation data to be scored on while training.
        :param leaderboard_frame: H2OFrame with test data to be scored on in the leaderboard.

        :returns: An H2OAutoML object.

        :examples:
        >>> # Set up an H2OAutoML object
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> # Launch H2OAutoML
        >>> aml.train(y=y, training_frame=training_frame)
        """
        ncols = training_frame.ncols
        names = training_frame.names

        #Minimal required arguments are training_frame and y (response)
        if y is None:
            raise ValueError('The response column (y) is not set; please set it to the name of the column that you are trying to predict in your data.')
        else:
            assert_is_type(y,int,str)
            if is_type(y, int):
                if not (-ncols <= y < ncols):
                    raise H2OValueError("Column %d does not exist in the training frame" % y)
                y = names[y]
            else:
                if y not in names:
                    raise H2OValueError("Column %s does not exist in the training frame" % y)
            input_spec = {
                'response_column': y,
            }

        if training_frame is None:
            raise ValueError('The training frame is not set!')
        else:
            assert_is_type(training_frame, H2OFrame)
            input_spec['training_frame'] = training_frame.frame_id

        if fold_column is not None:
            assert_is_type(fold_column,int,str)
            input_spec['fold_column'] = fold_column

        if weights_column is not None:
            assert_is_type(weights_column,int,str)
            input_spec['weights_column'] = weights_column

        if validation_frame is not None:
            assert_is_type(training_frame, H2OFrame)
            input_spec['validation_frame'] = validation_frame.frame_id

        if leaderboard_frame is not None:
            assert_is_type(training_frame, H2OFrame)
            input_spec['leaderboard_frame'] = leaderboard_frame.frame_id

        if x is not None:
            assert_is_type(x,list)
            xset = set()
            if is_type(x, int, str): x = [x]
            for xi in x:
                if is_type(xi, int):
                    if not (-ncols <= xi < ncols):
                        raise H2OValueError("Column %d does not exist in the training frame" % xi)
                    xset.add(names[xi])
                else:
                    if xi not in names:
                        raise H2OValueError("Column %s not in the training frame" % xi)
                    xset.add(xi)
            x = list(xset)
            ignored_columns = set(names) - {y} - set(x)
            if fold_column is not None: ignored_columns = ignored_columns - set(fold_column)
            if weights_column is not None: ignored_columns = ignored_columns - set(weights_column)
            input_spec['ignored_columns'] = list(ignored_columns)

        automl_build_params = dict(input_spec = input_spec)

        # NOTE: if the user hasn't specified some block of parameters don't send them!
        # This lets the back end use the defaults.
        automl_build_params['build_control'] = self.build_control

        resp = h2o.api('POST /99/AutoMLBuilder', json=automl_build_params)
        if 'job' not in resp:
            print("Exception from the back end: ")
            print(resp)
            return

        self._job = H2OJob(resp['job'], "AutoML")
        self._automl_key = self._job.dest_key
        self._job.poll()
        self._fetch()
        if self.project_name is None:
            self.project_name = "automl_" + training_frame.frame_id

    #---------------------------------------------------------------------------
    # Predict with AutoML
    #---------------------------------------------------------------------------
    def predict(self, test_data):
        """
        Predict on a dataset.

        :param H2OFrame test_data: Data on which to make predictions.

        :returns: A new H2OFrame of predictions.

        :examples:
        >>> #Set up an H2OAutoML object
        >>> aml = H2OAutoML(max_runtime_secs=30)
        >>> # Launch H2OAutoML
        >>> aml.train(y=y, training_frame=training_frame)
        >>> #Predict with #1 model from H2OAutoML leaderboard
        >>> aml.predict(test_data)

        """
        if self._fetch():
            self._model = h2o.get_model(self._leader_id)
            return self._model.predict(test_data)
        print("No model built yet...")

    #-------------------------------------------------------------------------------------------------------------------
    # Private
    #-------------------------------------------------------------------------------------------------------------------
    def _fetch(self):
        res = h2o.api("GET /99/AutoML/" + self._automl_key)
        leaderboard_list = [key["name"] for key in res['leaderboard']['models']]

        if leaderboard_list is not None and len(leaderboard_list) > 0:
            self._leader_id = leaderboard_list[0]
        else:
            self._leader_id = None
        self._leaderboard = res["leaderboard_table"]
        return self._leader_id is not None

    def _get_params(self):
        res = h2o.api("GET /99/AutoML/" + self._automl_key)
        return res

