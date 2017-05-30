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
import java.util.Properties;

import static water.util.Log.Level.*;

/**
 *  Log for H2O.
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
  private static final String HTTPD = "HTTPD";
  public enum Level {
    FATAL((byte)0), ERROR((byte)1), WARN((byte)2), INFO((byte)3),
    DEBUG((byte)4), TRACE((byte)5);

    private byte numLevel;
    Level(byte numLevel) {
      this.numLevel = numLevel;
    }

    public byte getLevel(){
      return numLevel;
    }

    public static Level fromString(String level) {
      if(level == null){
        throw new IllegalArgumentException("Unsupported log level");
      }
      return Level.valueOf(level.toUpperCase());
    }
  }

  private static org.apache.log4j.Logger logger = null;
  private static String logDir = null;
  // List for messages to be logged before the logging is fully initialized (startup buffering)
  private static ArrayList<String> initialMsgs = new ArrayList<>();
  private static Level currentLevel = INFO;
  private static boolean quietLogging = false;
  // Common prefix for logged messages
  private static String logPrefix;

  /** Basic logging methods */
  public static void fatal(Object... objs) { log(FATAL, objs); }
  public static void err(Object... objs) { log(ERROR, objs); }
  public static void warn(Object... objs) { log(WARN, objs); }
  public static void info(Object... objs) { log(INFO, objs); }
  public static void debug(Object... objs) { log(DEBUG, objs); }
  public static void trace(Object... objs) { log(TRACE, objs); }
  public static void log(Level level, Object... objs) { if( currentLevel.numLevel >= level.numLevel ) write(level, objs); }

  /** Log exception */
  public static void err(Throwable ex) {
    StringWriter sw = new StringWriter();
    ex.printStackTrace(new PrintWriter(sw));
    err(sw.toString());
  }

  /** Log with custom specification whether to print to stdout or not */
  public static void info(String s, boolean stdout) { if( currentLevel.numLevel >= INFO.numLevel ) write(INFO, stdout, new String[]{s}); }

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

  public static boolean isLoggingEnabledFor(Level level){
    return currentLevel.numLevel >= level.numLevel;
  }

  public static void flushStdout() {
    if (initialMsgs != null) {
      for (String s : initialMsgs) {
        System.out.println(s);
      }
      initialMsgs.clear();
    }
  }

  /**
   * Get directory where the logs are stored
   * This method returns valid value only when logging has been initialized
   * */
  public static String getLogDir(){
    if (logDir == null) {
      throw new RuntimeException("logDir not yet defined!");
    }
    return logDir;
  }

  public static Level getCurrentLogLevel(){
    return currentLevel;
  }

  /** Get full path to a log file of specified log level */
  public static String getLogFilePath(Level level) {
    return getLogDir() + File.separator + getLogFileName(level);
  }

  /** Get path to HTTPD logs. They are special logs and therefore are handled separately */
  public static String getLogFilePathHTTPD(){
    return getLogDir() + File.separator + getLogFileNameHTTPD();
  }

  public static boolean isLoggerInitialized(){
    return logDir != null;
  }

  public static void setLevel(Level level){
    currentLevel = level;
  }

  /** Get names of all log files */
  public static String[] getLogFileNames(){
    return new String[]{
            getLogFileName(FATAL),
            getLogFileName(ERROR),
            getLogFileName(WARN),
            getLogFileName(INFO),
            getLogFileName(DEBUG),
            getLogFileName(TRACE),
            getLogFileNameHTTPD()
    };
  }

  /** Get file name for log file of specified log level */
  public static String getLogFileName(Level level){
    return getLogFileNamePrefix() + "-" + level.numLevel + "-" + level.toString() + ".log";
  }

  /** Get name of HTTPD logs. They are special logs and therefore are handled separately */
  public static String getLogFileNameHTTPD(){
    return getLogFileNamePrefix() + "-" + HTTPD + ".log";
  }

  /**
   * Get Prefix for each log file.
   * This method can be used only after the logging has been initialized */
  private static String getLogFileNamePrefix() {
    if(H2O.SELF_ADDRESS == null){
      throw new RuntimeException("H2O.SELF_ADDRESS not yet defined");
    }
    String ip = H2O.SELF_ADDRESS.getHostAddress();
    int port = H2O.API_PORT;
    String portString = Integer.toString(port);
    return "h2o_" + ip + "_" + portString;
  }

  private static void setPropertiesForLevel(Properties p, String logger, Level level) {
   setPropertiesFor(p, logger, "%m%n", level.name(), getLogFilePath(level));
  }

  private static void setPropertiesForHTTPD(Properties p){
    p.setProperty("log4j.logger.water.api.RequestServer",       "TRACE, " + HTTPD);
    p.setProperty("log4j.additivity.water.api.RequestServer",   "false");
    setPropertiesFor(p, HTTPD, "%d{ISO8601} %m%n", HTTPD.toLowerCase(), getLogFilePathHTTPD());
  }

  private static void setPropertiesFor(Properties p, String logger, String pattern, String level, String logPath){
    p.setProperty("log4j.appender." + logger,                               "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender." + logger + ".Threshold",                level);
    p.setProperty("log4j.appender." + logger + ".File",                     logPath);
    p.setProperty("log4j.appender." + logger + ".MaxFileSize",              "1MB");
    p.setProperty("log4j.appender." + logger + ".MaxBackupIndex",           "3");
    p.setProperty("log4j.appender." + logger + ".layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender." + logger + ".layout.ConversionPattern",  pattern);
  }

  private static void setLog4jProperties(Properties p) {
    String appenders = new String[]{
            "TRACE, R6",
            "TRACE, R5, R6",
            "TRACE, R4, R5, R6",
            "TRACE, R3, R4, R5, R6",
            "TRACE, R2, R3, R4, R5, R6",
            "TRACE, R1, R2, R3, R4, R5, R6",
    }[currentLevel.numLevel];
    p.setProperty("log4j.logger.water.default", appenders);
    p.setProperty("log4j.additivity.water.default",   "false");

    setPropertiesForLevel(p,"R1", TRACE);
    setPropertiesForLevel(p,"R2", DEBUG);
    setPropertiesForLevel(p,"R3", INFO);
    setPropertiesForLevel(p,"R4", WARN);
    setPropertiesForLevel(p,"R5", ERROR);
    setPropertiesForLevel(p,"R6", FATAL);
    setPropertiesForHTTPD(p);

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
      setLog4jProperties(p);
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
  private static String header(Level lvl) {
    String threadName = StringUtils.ofFixedLength(Thread.currentThread().getName() + " ", 10);
    return String.format("%s %s %s%s: ", Timer.nowAsLogString(), logPrefix, threadName, lvl);
  }

  /** Determine whether to print to stdout or not and pass the call for further processing */
  private static void write(Level lvl, Object objs[]) {
    boolean writeToStdout = (lvl.numLevel <= currentLevel.numLevel);
    write(lvl, writeToStdout, objs);
  }

  /** Initialize the log if necessary, log buffered messages and pass the call for further processing */
  private static void write(Level lvl, boolean stdout, Object objs[]) {
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

  private static void write0(Level lvl, boolean stdout, String s) {
    StringBuilder sb = new StringBuilder();
    String hdr = header(lvl);   // Common header for all lines
    write0(sb, hdr, s);

    // stdout first - in case log4j dies failing to init or write something
    if(stdout && !quietLogging) System.out.println(sb);

    switch( lvl ) {
      case FATAL: logger.fatal(sb); break;
      case ERROR: logger.error(sb); break;
      case WARN: logger.warn(sb); break;
      case INFO: logger.info(sb); break;
      case DEBUG: logger.debug(sb); break;
      case TRACE: logger.trace(sb); break;
      default:
        throw new RuntimeException("Invalid log level");
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
