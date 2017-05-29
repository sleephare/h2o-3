package water.util;

import org.apache.log4j.H2OPropertyConfigurator;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import water.H2O;
import water.persist.PersistManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

/** Log for H2O.
 *
 *  OOME: when the VM is low on memory, OutOfMemoryError can be thrown in the
 *  logging framework while it is trying to print a message. In this case the
 *  first message that fails is recorded for later printout, and a number of
 *  messages can be discarded. The framework will attempt to print the recorded
 *  message later, and report the number of dropped messages, but this done in
 *  a best effort and lossy manner. Basically when an OOME occurs during
 *  logging, no guarantees are made about the messages.
 **/
abstract public class Log {
  private static final byte UNKNOWN = -1;
  private static final byte FATAL = 0;
  private static final byte ERROR = 1;
  private static final byte WARN = 2;
  private static final byte INFO = 3;
  private static final byte DEBUG = 4;
  private static final byte TRACE = 5;

/**
 * Public Log Level which hides the internal codes
 */
  public enum LEVEL {
    UNKNOWN(Log.UNKNOWN), FATAL(Log.FATAL), ERROR(Log.ERROR), WARN(Log.WARN), INFO(Log.INFO),
    DEBUG(Log.DEBUG), TRACE(Log.TRACE);

    private byte numLevel;
    LEVEL(byte numLevel) {
      this.numLevel = numLevel;
    }

    public byte getLevel(){
      return numLevel;
    }

    public static LEVEL fromString(String level) {
      if(level == null){
        return UNKNOWN;
      }
      try {
        return LEVEL.valueOf(level.toUpperCase());
      }catch (IllegalArgumentException e){
        return UNKNOWN;
      }
    }

    public static LEVEL fromNum(int level){
      switch (level){
        case Log.FATAL: return FATAL;
        case Log.ERROR: return ERROR;
        case Log.WARN: return WARN;
        case Log.INFO: return INFO;
        case Log.DEBUG: return DEBUG;
        case Log.TRACE: return TRACE;
        default: return UNKNOWN;
      }
    }
  }

  private static org.apache.log4j.Logger logger = null;
  private static String logDir = null;
  // List for messages to be logged before the logging is fully initialized (startup buffering)
  private static ArrayList<String> initialMsgs = new ArrayList<>();
  private static byte currentLevel = INFO;
  private static boolean quietLogging = false;
  // Common prefix for logged messages
  private static String logPrefix;

  /** Basic logging methods */
  public static void fatal(Object... objs) { log(FATAL,objs); }
  public static void err(Object... objs) { log(ERROR, objs); }
  public static void warn(Object... objs) { log(WARN, objs); }
  public static void info(Object... objs) { log(INFO, objs); }
  public static void debug(Object... objs) { log(DEBUG, objs); }
  public static void trace(Object... objs) { log(TRACE, objs); }
  public static void log(int level, Object... objs) { if( currentLevel >= level ) write(level, objs); }

  /** Log exception */
  public static void err(Throwable ex) {
    StringWriter sw = new StringWriter();
    ex.printStackTrace(new PrintWriter(sw));
    err(sw.toString());
  }

  /** Log with custom specification whether to print to stdout or not */
  public static void info(String s, boolean stdout) { if( currentLevel >= INFO ) write(INFO, stdout, new String[]{s}); }

  /** Log to htttp log */
  public static void httpd(String method, String uri, int status, long deltaMillis) {
    org.apache.log4j.Logger l = LogManager.getLogger(water.api.RequestServer.class);
    String msg = String.format("  %-6s  %3d  %6d ms  %s", method, status, deltaMillis, uri);
    l.info(msg);
  }

  /** This call *throws* an unchecked exception and never returns (after logging).*/
  public static RuntimeException throwErr(Throwable e) {
    err(e); // Log it
    throw e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e); // Throw it
  }

  public static void ignore(Throwable e) {
    ignore(e,"[h2o] Problem ignored: ");
  }

  public static void ignore(Throwable e, String msg) {
    ignore(e, msg, true);
  }

  public static void ignore(Throwable e, String msg, boolean printException) {
    debug(msg + (printException? e.toString() : ""));
  }

  public static void setQuiet(boolean q) { quietLogging = q; }
  public static boolean getQuiet() { return quietLogging; }

  public static void flushStdout() {
    if (initialMsgs != null) {
      for (String s : initialMsgs) {
        System.out.println(s);
      }
      initialMsgs.clear();
    }
  }

  /** Get directory where the logs are stored */
  public static String getLogDir() throws Exception {
    if (logDir == null) {
      throw new Exception("logDir not yet defined");
    }
    return logDir;
  }

  public static LEVEL getCurrentLogLevel(){
    return LEVEL.fromNum(currentLevel);
  }

  /** Get full path to a log file of specified log level */
  public static String getLogFilePath(String level) throws Exception {
    return getLogDir() + File.separator + getLogFileName(level);
  }

  public static void init(String slvl, boolean quiet) {
    LEVEL lvl = LEVEL.fromString(slvl);
    if( lvl != LEVEL.UNKNOWN) currentLevel = lvl.getLevel();
    quietLogging = quiet;
  }

  /** Get names of all log files */
  public static String[] getLogFileNames() throws Exception {
    return new String[]{
            getLogFileName("fatal"),
            getLogFileName("error"),
            getLogFileName("warn"),
            getLogFileName("info"),
            getLogFileName("debug"),
            getLogFileName("trace"),
            getLogFileName("httpd")
    };
  }
  /** Get file name for log file of specified log level */
  private static String getLogFileName(String level) throws Exception {
    if(level.equals("httpd")){
      return getLogFileNamePrefix() + "-HTTPD.log";
    }else{
      LEVEL lvl = LEVEL.fromString(level);
      if(lvl.equals(LEVEL.UNKNOWN)) {
        throw new RuntimeException("Unknown level: " + level);
      } else {
        return getLogFileNamePrefix() + "-" + lvl.getLevel() + "-" + lvl.toString() + ".log";
      }
    }
  }

  /** Get Prefix for each log file */
  private static String getLogFileNamePrefix() throws Exception {
    if(H2O.SELF_ADDRESS == null){
      throw new Exception("H2O.SELF_ADDRESS not yet defined");
    }
    String ip = H2O.SELF_ADDRESS.getHostAddress();
    int port = H2O.API_PORT;
    String portString = Integer.toString(port);
    return "h2o_" + ip + "_" + portString;
  }

  private static void setLog4jProperties(java.util.Properties p) throws Exception {
    String appenders = new String[]{
            "TRACE, R6",
            "TRACE, R5, R6",
            "TRACE, R4, R5, R6",
            "TRACE, R3, R4, R5, R6",
            "TRACE, R2, R3, R4, R5, R6",
            "TRACE, R1, R2, R3, R4, R5, R6",
    }[currentLevel];
    p.setProperty("log4j.logger.water.default", appenders);
    p.setProperty("log4j.additivity.water.default",   "false");

    p.setProperty("log4j.appender.R1",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R1.Threshold",                "TRACE");
    p.setProperty("log4j.appender.R1.File",                     getLogFilePath("trace"));
    p.setProperty("log4j.appender.R1.MaxFileSize",              "1MB");
    p.setProperty("log4j.appender.R1.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R1.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R1.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R2",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R2.Threshold",                "DEBUG");
    p.setProperty("log4j.appender.R2.File",                     getLogFilePath("debug"));
    p.setProperty("log4j.appender.R2.MaxFileSize",              "3MB");
    p.setProperty("log4j.appender.R2.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R2.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R2.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R3",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R3.Threshold",                "INFO");
    p.setProperty("log4j.appender.R3.File",                      getLogFilePath("info"));
    p.setProperty("log4j.appender.R3.MaxFileSize",              "2MB");
    p.setProperty("log4j.appender.R3.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R3.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R3.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R4",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R4.Threshold",                "WARN");
    p.setProperty("log4j.appender.R4.File",                     getLogFilePath("warn"));
    p.setProperty("log4j.appender.R4.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R4.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R4.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R4.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R5",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R5.Threshold",                "ERROR");
    p.setProperty("log4j.appender.R5.File",                     getLogFilePath("error"));
    p.setProperty("log4j.appender.R5.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R5.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R5.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R5.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R6",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R6.Threshold",                "FATAL");
    p.setProperty("log4j.appender.R6.File",                     getLogFilePath("fatal"));
    p.setProperty("log4j.appender.R6.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R6.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R6.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R6.layout.ConversionPattern", "%m%n");

    // HTTPD logging
    p.setProperty("log4j.logger.water.api.RequestServer",       "TRACE, HTTPD");
    p.setProperty("log4j.additivity.water.api.RequestServer",   "false");

    p.setProperty("log4j.appender.HTTPD",                       "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.HTTPD.Threshold",             "TRACE");
    p.setProperty("log4j.appender.HTTPD.File",                  getLogFilePath("httpd"));
    p.setProperty("log4j.appender.HTTPD.MaxFileSize",           "1MB");
    p.setProperty("log4j.appender.HTTPD.MaxBackupIndex",        "3");
    p.setProperty("log4j.appender.HTTPD.layout",                "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.HTTPD.layout.ConversionPattern", "%d{ISO8601} %m%n");

    // Turn down the logging for some class hierarchies.
    p.setProperty("log4j.logger.org.apache.http",               "WARN");
    p.setProperty("log4j.logger.com.amazonaws",                 "WARN");
    p.setProperty("log4j.logger.org.apache.hadoop",             "WARN");
    p.setProperty("log4j.logger.org.jets3t.service",            "WARN");
    p.setProperty("log4j.logger.org.reflections.Reflections",   "ERROR");
    p.setProperty("log4j.logger.com.brsanthu.googleanalytics",  "ERROR");

    // Turn down the logging for external libraries that Orc parser depends on
    p.setProperty("log4j.logger.org.apache.hadoop.util.NativeCodeLoader", "ERROR");

    // See the following document for information about the pattern layout.
    // http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html
    //
    //  Uncomment this line to find the source of unwanted messages.
    //     p.setProperty("log4j.appender.R1.layout.ConversionPattern", "%p %C %m%n");
  }

  private static File defaultLogDir() {
    boolean windowsPath = H2O.ICE_ROOT.toString().matches("^[a-zA-Z]:.*");
    File dir;
    // Use ice folder if local, or default
    if (windowsPath) {
      dir = new File(H2O.ICE_ROOT.toString());
    } else if (H2O.ICE_ROOT.getScheme() == null || PersistManager.Schemes.FILE.equals(H2O.ICE_ROOT.getScheme())) {
      dir = new File(H2O.ICE_ROOT.getPath());
    } else {
      dir = new File(H2O.DEFAULT_ICE_ROOT());
    }

    try {
      // create temp directory inside the log folder so the logs don't overlap
      return FileUtils.createUniqueDirectory(dir.getAbsolutePath(), "h2ologs");
    }catch (IOException e){
      // fail if the directory couldn't be created
      throw new RuntimeException(e);
    }
  }

  private static synchronized void initializeLogger() {
    if( logger != null ) return; // Test again under lock

    String log4jConfiguration = System.getProperty ("h2o.log4j.configuration");
    boolean log4jConfigurationProvided = log4jConfiguration != null;

    if (log4jConfigurationProvided) {
      PropertyConfigurator.configure(log4jConfiguration);
    } else {
      // Create some default properties on the fly if we aren't using a provided configuration.
      // H2O creates the log setup itself on the fly in code.
      java.util.Properties p = new java.util.Properties();
      if (H2O.ARGS.log_dir != null) {
        logDir = new File(H2O.ARGS.log_dir).getAbsolutePath();
      } else {
        logDir = defaultLogDir().getAbsolutePath();
      }
      try {
        setLog4jProperties(p);
      }catch (Exception e){
        // this can't happen since at the time the setLog4jProperties method is called, both logDir and SELF_ADDRESS
        // are set up
        throw new RuntimeException(e);
      }

      boolean launchedWithHadoopJar = H2O.ARGS.launchedWithHadoopJar();
      // For the Hadoop case, force H2O to specify the logging setup since we don't care
      // about any hadoop log setup, anyway.
      //
      // For the Sparkling Water case, we will have inherited the log4j configuration,
      // so append to it rather than whack it.
      if (!launchedWithHadoopJar && H2O.haveInheritedLog4jConfiguration()) {
        // Use a modified log4j property configurator to append rather than create a new log4j configuration.
        H2OPropertyConfigurator.configure(p);
      }
      else {
        PropertyConfigurator.configure(p);
      }
    }
    logger = LogManager.getLogger("water.default");
  }

  private static void setLogPrefix(){
    String host = H2O.SELF_ADDRESS.getHostAddress();
    logPrefix = StringUtils.ofFixedLength(host + ":" + H2O.API_PORT + " ", 22)
            + StringUtils.ofFixedLength(H2O.PID + " ", 6);
  }

  /** Build a header for all lines in a single message */
  private static String header(int lvl) {
    String threadName = StringUtils.ofFixedLength(Thread.currentThread().getName() + " ", 10);
    return String.format("%s %s %s%s: ", Timer.nowAsLogString(), logPrefix, threadName, LEVEL.fromNum(lvl));
  }

  /** Determine whether to print to stdout or not and pass the call for further processing */
  private static void write(int lvl, Object objs[]) {
    boolean writeToStdout = (lvl <= currentLevel);
    write(lvl, writeToStdout, objs);
  }

  /** Initialize the log if necessary, log buffered messages and pass the call for further processing */
  private static void write(int lvl, boolean stdout, Object objs[]) {
    StringBuilder sb = new StringBuilder();
    for( Object o : objs ) sb.append(o);
    String res = sb.toString();
    if( H2O.SELF_ADDRESS == null ) { // Oops, need to buffer until we can do a proper header
      initialMsgs.add(res);
      return;
    }
    if( initialMsgs != null ) {   // Ahh, dump any initial buffering
      setLogPrefix();
      // this is a good time to initialize log4j since H2O.SELF_ADDRESS is already known
      initializeLogger();
      ArrayList<String> bufmsgs = initialMsgs;  initialMsgs = null;
      if (bufmsgs != null) for( String s : bufmsgs ) write0(INFO, true, s);
    }
    write0(lvl, stdout, res);
  }

  private static void write0(int lvl, boolean stdout, String s) {
    StringBuilder sb = new StringBuilder();
    String hdr = header(lvl);   // Common header for all lines
    write0(sb, hdr, s);

    // stdout first - in case log4j dies failing to init or write something
    if(stdout && !quietLogging) System.out.println(sb);

    switch( lvl ) {
      case FATAL: logger.fatal(sb); break;
      case ERROR: logger.error(sb); break;
      case WARN: logger.warn (sb); break;
      case INFO: logger.info (sb); break;
      case DEBUG: logger.debug(sb); break;
      case TRACE: logger.trace(sb); break;
      default:
        logger.error("Invalid log level requested");
        logger.error(s);
    }
  }

  private static void write0(StringBuilder sb, String hdr, String s) {
    if( s.contains("\n") ) {
      for( String s2 : s.split("\n") ) { write0(sb, hdr, s2); sb.append("\n"); }
      sb.setLength(sb.length() - 1);
    } else {
      sb.append(hdr).append(s);
    }
  }

  //-----------------------------------------------------------------
  // POST support for debugging embedded configurations.
  //-----------------------------------------------------------------

  /**
   * POST stands for "Power on self test".
   * Stamp a POST code to /tmp.
   * This is for bringup, when no logging or stdout I/O is reliable.
   * (Especially when embedded, such as in hadoop mapreduce, for example.)
   *
   * @param n POST code.
   * @param s String to emit.
   */
  public static void POST(int n, String s) {
    System.out.println("POST " + n + ": " + s);
  }

  public static void POST(int n, Exception e) {
    if (e.getMessage() != null) {
      POST(n, e.getMessage());
    }
    POST(n, e.toString());
    StackTraceElement[] els = e.getStackTrace();
    for (StackTraceElement el : els) {
      POST(n, el.toString());
    }
  }
}
