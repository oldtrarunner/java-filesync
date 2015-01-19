// Info - http://docs.oracle.com/javase/1.5.0/docs/api/java/io/File.html
import java.io.*;
import java.io.IOException;
import java.io.File;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;

public class fileSync
{
  private static String lineSep = System.getProperty("line.separator");
  private static String usage = new String (
    "java fileSync {-v} {-d} {-p} <source> <target>" + lineSep +
    lineSep +
    "  -p = Preview mode. Identifies changes that would be made to existing directories." + lineSep +
    "       Backup is not performed. Mutually exclusive with -v." + lineSep +
    "  -d = Record debug info to file fileSyncDebug.txt in save directory as fileSync.class." + lineSep +
    "  -v = Verbose mode. Identify updates in std out. Mutually exclusive with -p." + lineSep +
    lineSep +
    "  If both -v and -p are specified, -p is assumed and -v is ignored." + lineSep);
  private static String s;
  private static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
  private static String debugInfo;
  private static Writer debugFile = null;
  private static boolean debugMode = false;
  private static boolean previewMode = false;
  private static boolean verboseMode = false;

  public static void main(String args[])
  {
    // Debug file. Same location as .class file. Resource is prefixed with "file:",
    // which we need to remove.
    ClassLoader loader = fileSync.class.getClassLoader();
    String debugLoc = loader.getResource("fileSync.class").toString().
                        replaceAll("fileSync.class", "fileSyncDebug.txt").
                        substring(5);
    System.out.println ("debugLoc = " + debugLoc + lineSep);

    File fromLoc = null;
    File toLoc = null;

    // If more than four arguments are specified, we know we have a problem.
    if (args.length > 4)
    {
      s = new String ("ERROR: Too many parameters specified.");
      System.out.println (s + lineSep + usage);
      return;
    }

    // Obvious request for help
    if (args[0].equals("h") || args[0].equals("H") ||
        args[0].equals("-h") || args[0].equals("-H") ||
        args[0].equals("/h") || args[0].equals("/H") ||
        args[0].equals("help") ||
        args[0].equals("-help") ||
        args[0].equals("/help"))
    {
      System.out.println (usage);
      System.exit(0);
    }

    // Get optional debug flag.
    if (args[0].equals("-d") || args[1].equals("-d"))
    {
      debugMode = true;
    }

    // Get optional preview flag.
    if (args[0].equals("-p") || args[1].equals("-p"))
    {
      previewMode = true;
    }

    // Get optional verbose flag.
    if (args[0].equals("-v") || args[1].equals("-v"))
    {
      // Ignore if preview mode also requested.
      if ( ! args[0].equals("-p") &&  ! args[1].equals("-p"))
      {
        verboseMode = true;
      }
    }

    // Both optional flags are specified, skip two args to get source and target.
    if ((previewMode || verboseMode) && debugMode)
    {
      fromLoc = new File (args[2]);
      toLoc = new File (args[3]);
    }

    // One optional flag is specified, skip one arg to get source and target.
    else if (previewMode || debugMode || verboseMode)
    {
      fromLoc = new File (args[1]);
      toLoc = new File (args[2]);
    }

    // No optional flags
    else
    {
      fromLoc = new File (args[0]);
      toLoc = new File (args[1]);
    }

    // Initialize utility.
    OsUtils.getOsName();

    try
    {
      debugFile = new BufferedWriter
                        (new OutputStreamWriter
                               (new FileOutputStream(debugLoc),
                                "utf-8"));

      s = new String ("Running fileSync with debugMode = " + debugMode +
                          " and previewMode = " + previewMode + lineSep +
                      "  Source: " + fromLoc.getCanonicalPath() + lineSep +
                      "  Target: " + toLoc.getCanonicalPath() + lineSep);
      System.out.println (s);
      writeDebug (s);

      // If initial toLoc does not exist, create it.
      if ( ! toLoc.exists())
      {
        if ( ! previewMode)
        {
          // Makes the whole hierarchy as necessary.
          toLoc.mkdirs(); 

          if (verboseMode)
          {
            System.out.println ("Created directory " + toLoc.getCanonicalPath() + lineSep);
          }
        }
        else
        {
          s = new String("ERROR: It is not possible to run in preview mode when target location does not exist.");
          System.out.println (s);
          writeDebug (s);
        }

        writeDebug ("toLoc did not exist.\n");
      }

      // Note, can't really do anything if in previewMode and toLoc did not exist.
      // Process request only if from/to are directories and are not the same.
      if (fromLoc.exists() && fromLoc.isDirectory() &&
          toLoc.exists() && toLoc.isDirectory() &&
          fromLoc.compareTo (toLoc) != 0)
      {
        processLoc (fromLoc, toLoc);
      }
      else
      {
        // Error
        s = new String ("ERROR: Parameters do not specify directories." + lineSep +
                              " Source Location: " + fromLoc.getName() + lineSep +
                              " Target Location: " + toLoc.getName());
        System.out.println (s);

        writeDebug (s);
      }
    }
    catch (IOException ex)
    {
      System.err.println (ex);
    }
    finally
    {
      try {debugFile.close();} catch (Exception ex) {System.err.println (ex + lineSep + "During File.close");}
    }
  }

  private static void writeDebug (String debugInfo)
  {
    if (debugMode)
    {
      try
      {
        // Ensure line breaks.
        debugInfo += "\n";

        // Allow use of "\n" in String for line breaks.
        debugFile.write (debugInfo.replaceAll("\n", lineSep));
      }
      catch (IOException ex)
      {
        System.err.println (ex + "\n" +
                            "writeDebug() with debugInfo: " + debugInfo);
      }
    }
  }

  // Assumes fromLoc and toLoc are directories.
  private static void processLoc (File fromLoc, File toLoc)
  {
    try
    {
      s = new String ("processLoc() called for" + lineSep +
                      "  fromLoc: " + fromLoc.getCanonicalPath() + lineSep +
                      "  toLoc: " + toLoc.getCanonicalPath()+ lineSep);
    }
    // getCanonicalPath() failed
    catch (IOException ex)
    {
      s = new String (ex.toString() + lineSep +
                      "During processLoc() call for" + lineSep +
                      "  fromLoc: " + fromLoc.getName() + lineSep +
                      "  toLoc: " + toLoc.getName()+ lineSep);
    }
    writeDebug (s);

    // Remove To entries that do not exist in From directory.
    File [] fromFiles = fromLoc.listFiles();
    int fromLength = fromFiles.length;
    for (File toEntry: toLoc.listFiles())
    {
      // Check if current To entry exists in the From directory.
      boolean notFound = true;
      int fromIndex = 0;
      while (notFound && fromIndex < fromFiles.length)
      {
        if (fromFiles[fromIndex].getName().equals(toEntry.getName()))
        {
          notFound = false;
        }
        else
        {
          fromIndex++;
        }
      }

      // To entry not found in the From directory.
      if (notFound)
      {
        writeDebug ("toEntry " + toEntry.getName() + " not found in fromFiles.\n");

        // Delete To entry. A directory must be empty to delete.
        if (toEntry.isDirectory())
        {
          deleteDirectory(toEntry);
        }

        // Delete To file.
        else
        {
          s = new String ("Deleting toEntry " + toEntry.getName() + lineSep);
          writeDebug (s);

          if ( ! previewMode)
          {
            if (verboseMode)
            {
              try{System.out.println ("Deleted file " + toEntry.getCanonicalPath() + lineSep);}
              catch(IOException ex){s = new String (ex.toString());}
            }

            toEntry.delete();
          }
          else
          {
            try{s = new String ("Delete " + toEntry.getCanonicalPath() + lineSep);}
            catch(IOException ex){s = new String (ex.toString());}
            System.out.println (s);
          }
        }
      }
    }

    // Process each From entry to determine if need to make an update at To location.
    for (File fromEntry: fromLoc.listFiles())
    {
      writeDebug ("processing fromEntry " + fromEntry.getName() + "\n");

      try
      {
        String toPath = toLoc.getCanonicalPath() + File.separator + fromEntry.getName();

        File toEntry = new File(toPath);

        // Process From directory.
        if (fromEntry.isDirectory())
        {
          writeDebug ("fromEntry " + fromEntry.getName() + " is a directory.\n");

          // If toEntry does not exist, create as directory.
          if ( ! toEntry.exists())
          {
            writeDebug ("To directory " + toEntry.getName() + " does not exist.\n");

            if ( ! previewMode)
            {
              toEntry.mkdir();

              writeDebug ("Created To directory " + toEntry.getName() + "\n");

              if (verboseMode)
              {
                System.out.println ("Created directory " + toEntry.getCanonicalPath() + lineSep);
              }

              processLoc (fromEntry, toEntry);
            }
            else
            {
              s = new String ("Cannot create To directory " + toEntry.getName() + " in preview mode." + lineSep +
                          "Therefore, further processing of " + fromEntry.getName() + " is not possible." + lineSep);
              writeDebug (s);
              System.out.println (s);
            }
          }

          // To directory exists.
          else
          {
            writeDebug ("To directory " + toEntry.getName() + " exists.\n");

            processLoc (fromEntry, toEntry);
          }
        }

        // Process From file.
        else
        {
          writeDebug ("fromEntry " + fromEntry.getName() + " is a file.\n");

          // Process From only if not a link (Windows shortcuts will be processed as files)
          if ( ! isLink (fromEntry))
          {
            writeDebug ("fromEntry " + fromEntry.getName() + " is not a Unix symbolic link.\n");

            // If toEntry exists, check timestamps.
            if (toEntry.exists())
            {
              writeDebug ("toEntry " + toEntry.getName() + " exists.\n");

              // Convert once for multiple uses.
              String fromModTm = sdf.format(fromEntry.lastModified());
              String toModTm = sdf.format(toEntry.lastModified());

              // If From timestamp is more recent, replace To with From.
              // Time is in units of msec, so add 999 to To time in case To device does not 
              // keep track of msec!
              if (fromEntry.lastModified() > (toEntry.lastModified() + 999))
              {
                // String for debug.
                s = new String ("fromEntry " + fromEntry.getName() + " timestamp " + fromModTm +
                                   " is later than toEntry timestamp " + toModTm + "." + lineSep +
                                   " toEntry will be replaced by fromEntry." + lineSep);
                writeDebug (s);

                // String for preview and normal logging.
                s = new String (toEntry.getCanonicalPath() + lineSep +
                                "  (from timestamp " + fromModTm + " / to timestamp " + toModTm + ")" + lineSep);
                //s = new String (toEntry.getCanonicalPath() + lineSep +
                //                "  (from timestamp " + fromEntry.lastModified() + " / to timestamp " +
                //                toEntry.lastModified() + ")" + lineSep);


                if ( ! previewMode)
                {
                  toEntry.delete();
                }
                else
                {
                  String s2 = new String ("Replace " + s);
                  System.out.println (s2);
                }

                copyFile (fromEntry, toEntry);

                if ( ! previewMode)
                {
                  // Explicitely set To timestamp to match From.
                  toEntry.setLastModified (fromEntry.lastModified());

                  if (verboseMode)
                  {
                    System.out.println ("Replaced " + s);
                  }
                }
              }

              // Else, do nothing if From timestamp is not more recent.
              else
              {
                writeDebug ("fromEntry " + fromEntry.getName() + " timestamp " + fromModTm +
                                   " is NOT later than toEntry timestamp " + toModTm +
                                   ".\ntoEntry will NOT be replaced by fromEntry.\n");

                if (previewMode)
                {
                  s = new String ("Do not replace " + toEntry.getCanonicalPath() + lineSep +
                                  "  (from timestamp " + fromModTm + " / to timestamp " + toModTm + ")" + lineSep);
                  System.out.println (s);
                }
              }
            }

            // Else, create To File
            else
            {
              writeDebug ("toEntry " + toEntry.getName() + " does NOT exist." + lineSep +
                                  "fromEntry " + fromEntry.getName() + " will be copied to toEntry.\n");

              copyFile (fromEntry, toEntry);

              if ( ! previewMode)
              {
                // Explicitely set To timestamp to match From.
                toEntry.setLastModified (fromEntry.lastModified());

                if (verboseMode)
                {
                  System.out.println ("Created file " + toEntry.getCanonicalPath() + lineSep);
                }
              }
              else
              {
                s = new String ("Create " + toEntry.getCanonicalPath() + lineSep);
                System.out.println (s);
              }
            }
          }

          // File is a Unix symbolic link.
          else
          {
            writeDebug ("fromEntry " + fromEntry.getName() + " is a Unix symbolic reference. File sync does not support these, so fromEntry is being ignored.\n");

            s = new String ("Ignoring link " + fromEntry.getCanonicalPath() + lineSep);
            System.out.println (s);
          }
        }
      }

      catch (IOException ex)
      {
        s = new String(ex + lineSep +
                              "Thrown by processLoc()" + lineSep +
                              "fromEntry: " + fromEntry.getName() + lineSep +
                              "toLoc: " + toLoc.getName());
        System.err.println(s);
        writeDebug (s);
      }
    }
  }

  // Recursive.
  private static void deleteDirectory(File path)
  {
    writeDebug ("deleteDirectory() called for " + path.getName() + "\n");

    if (path.exists())
    {
      writeDebug ("directory " + path.getName() + " exists.\n");

      File[] files = path.listFiles();
      for (int i = 0; i < files.length; i++)
      {
        writeDebug ("Processing entry " + files[i].getName() +
                           " from directory " + path.getName() + "\n");

        if (files[i].isDirectory())
        {
          writeDebug ("Entry " + files[i].getName() + " is a directory.\n");

          deleteDirectory(files[i]);
        }
        else
        {
          writeDebug ("Entry " + files[i].getName() + " is a file and will be deleted.\n");

          if ( ! previewMode)
          {
            if (verboseMode)
            {
              try{System.out.println ("Deleted file " + files[i].getCanonicalPath() + lineSep);}
              catch(IOException ex){s = new String (ex.toString());}
            }

            files[i].delete();
          }
          else
          {
            try{s = new String ("Delete " + files[i].getCanonicalPath() + lineSep);}
            catch (IOException ex){s = new String (ex.toString());}
            System.out.println (s);
          }
        }
      }
    }

    writeDebug ("Directory " + path.getName() + " is being deleted.\n");

    if ( ! previewMode)
    {
      if (verboseMode)
      {
        try{System.out.println ("Deleted directory " + path.getCanonicalPath() + lineSep);}
        catch(IOException ex){s = new String (ex.toString());}
      }

      path.delete();
    }
    else
    {
      try{s = new String ("Delete " + path.getCanonicalPath() + lineSep);}
      catch (IOException ex){s = new String(ex.toString());}
      System.out.println (s);
    }
  }

  private static void copyFile(File sourceFile, File destFile)
  {
    writeDebug ("copyFile() called for sourceFile " + sourceFile.getName() +
                       ", and destFile " + destFile.getName() + "\n");

    try
    {
      if (!sourceFile.exists())
      {
        return;
      }

      if ( ! previewMode)
      {
        if (!destFile.exists())
        {
          destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;
        source = new FileInputStream(sourceFile).getChannel();
        destination = new FileOutputStream(destFile).getChannel();

        if (destination != null && source != null)
        {
          destination.transferFrom(source, 0, source.size());
        }

        if (source != null)
        {
          source.close();
        }

        if (destination != null)
        {
          destination.close();
        }
      }
    }

    catch (IOException ex)
    {
      s = new String(ex + lineSep +
                            "Thrown by copyFile()." + lineSep +
                            "sourceFile: " + sourceFile.getName() + lineSep +
                            "destFile: " + destFile.getName());
      System.err.println(s);
      writeDebug (s);
    }
  }

  // Partial support - really only works for Unix symbolic links.
  // Windows shortcuts will be treated as regular files - just won't work in target location!
  // See http://www.scribblethink.org/Xfiles/javasymlinks.html
  private static boolean isLink(File file)
  {
    //writeDebug ("isLink() called for " + file.getName() + "\n");

    boolean retVal = false;

    try
    {
      if (OsUtils.isWindows())
      {
        retVal = false;
      }
      else if (!file.exists())
      {
	retVal = true;
      }
      else
      {
        String cnnpath = file.getCanonicalPath();
        String abspath = file.getAbsolutePath();
        retVal = !abspath.equals(cnnpath);
      }
    }
    catch(IOException ex)
    {
      System.err.println(ex);

      writeDebug (ex + "\n");

      retVal = true;
    }

    //writeDebug ("isLink() is returning " + retVal + "\n");

    return retVal;
  }

  public static final class OsUtils
  {
    private static String OS = null;
    public static String getOsName()
    {
      if(OS == null) { OS = System.getProperty("os.name"); }
      return OS;
    }
    public static boolean isWindows()
    {
      return getOsName().startsWith("Windows");
    }
  }

}
