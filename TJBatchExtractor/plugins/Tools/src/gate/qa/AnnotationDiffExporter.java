package gate.qa;

import gate.Annotation;
import gate.Document;
import gate.Utils;
import gate.util.AnnotationDiffer;
import gate.util.Strings;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * The class is used by Quality Assurance PR. As is documented in the
 * Quality Assurance PR, it links every document in the
 * document-stats.html with a file containing output of the annotation
 * diff tool for every annotation type provided as a parameter value in
 * that PR. The output in here is produced by this class. It generates
 * an html file.
 * 
 * @author niraj
 * 
 */
public class AnnotationDiffExporter {

  /**
   * Names of key and response annotation sets
   */
  private String keySetName, respSetName;

  /**
   * The document on which the annotation diff is performed
   */
  protected Document document;

  /**
   * Annotation Diff objects and the respective diff calculation results
   */
  protected Map<AnnotationDiffer, List<AnnotationDiffer.Pairing>> differs;

  /**
   * Constructor
   * 
   * @param differs a map containing instance of AnnotationDiffer
   *          objects (one for one annotation type) and a list of
   *          AnnotationDiffer.Pairing objects - each referring to an
   *          annotation in the document of that type
   * @param document document on which the annotation diff is performed.
   * @param keySetName name of the key annotation set
   * @param respSetName name of the response annotaiton set
   */
  public AnnotationDiffExporter(
          Map<AnnotationDiffer, List<AnnotationDiffer.Pairing>> differs,
          Document document, String keySetName, String respSetName) {
    this.differs = differs;
    this.document = document;
    this.keySetName = keySetName;
    this.respSetName = respSetName;
  }

  /**
   * Produces an html file containing html annotation diff output for
   * different annotation types
   * 
   * @param destinationFile
   * @throws IOException
   */
  public void export(File destinationFile) throws IOException {

    // get the document name
    String docName = document.getName();

    String nl = Strings.getNl();
    Writer fw = new BufferedWriter(new FileWriter(destinationFile));

    // write the header
    fw.write("<html><body>");

    // some metadata info (i.e. document name, annotation sets used for
    // diff
    // calculations etc.
    fw.write("<H2>Annotation Diff - comparing annotations! </H2>");
    fw.write("<TABLE cellpadding=\"5\" border=\"0\"");
    fw.write(nl);
    fw.write("<TR>" + nl);
    fw.write("\t<TH align=\"left\">&nbsp;</TH>" + nl);
    fw.write("\t<TH align=\"left\">Document</TH>" + nl);
    fw.write("\t<TH align=\"left\">Annotation Set</TH>" + nl);
    fw.write("</TR>" + nl);

    fw.write("<TR>" + nl);
    fw.write("\t<TH align=\"left\">Key</TH>" + nl);
    fw.write("\t<TD align=\"left\">" + docName + "</TD>" + nl);
    fw.write("\t<TD align=\"left\">" + keySetName + "</TD>" + nl);
    fw.write("</TR>" + nl);
    fw.write("<TR>" + nl);
    fw.write("\t<TH align=\"left\">Response</TH>" + nl);
    fw.write("\t<TD align=\"left\">" + docName + "</TD>" + nl);
    fw.write("\t<TD align=\"left\">" + respSetName + "</TD>" + nl);
    fw.write("</TR>" + nl);
    fw.write("</TABLE>" + nl);
    fw.write("<BR><BR><BR>" + nl);
    fw.write("<HR/>");

    // one differ output at a time
    for(AnnotationDiffer differ : differs.keySet()) {

      // results of the differ
      List<AnnotationDiffer.Pairing> pairings = differs.get(differ);

      // using DiffTable class (copied from AnnotationDifferGUI) with
      // minor
      // changes to produce almost identical presentation as produced by
      // the
      // annotation diff tool
      DiffTable diffTable = new DiffTable(pairings);

      // mention of the relevant annotation type
      fw.write("<H2>" + differ.getAnnotationType() + " annotations" + "</H2>");

      // write the results
      java.text.NumberFormat format = java.text.NumberFormat.getInstance();
      format.setMaximumFractionDigits(4);
      fw.write("Recall: " + format.format(differ.getRecallStrict()) + "<br>"
              + nl);
      fw.write("Precision: " + format.format(differ.getPrecisionStrict())
              + "<br>" + nl);
      fw.write("F-measure: " + format.format(differ.getFMeasureStrict(1))
              + "<br>" + nl);
      fw.write("<br>");
      fw.write("Correct: " + differ.getCorrectMatches() + "<br>" + nl);
      fw.write("Partially correct: " + differ.getPartiallyCorrectMatches()
              + "<br>" + nl);
      fw.write("Missing: " + differ.getMissing() + "<br>" + nl);
      fw.write("False positives: " + differ.getSpurious() + "<br>" + nl);

      // a table containing comparison of annotations
      fw.write("<table cellpadding=\"0\" border=\"1\">" + nl + "<TR>" + nl);

      int maxColIdx = diffTable.getColumnCount() - 1;
      for(int col = 0; col <= maxColIdx; col++) {
        fw.write("\t<TH align=\"left\">" + diffTable.getColumnName(col)
                + "</TH>" + nl);
      }
      fw.write("</TR>");
      int rowCnt = diffTable.getRowCount();
      for(int row = 0; row < rowCnt; row++) {
        fw.write("<TR>");
        for(int col = 0; col <= maxColIdx; col++) {
          Color bgCol = diffTable.getBackgroundAt(row, col);
          fw.write("\t<TD bgcolor=\"#"
                  + Integer.toHexString(bgCol.getRGB()).substring(2) + "\">"
                  + diffTable.getValueAt(row, col) + "</TD>" + nl);
        }
        fw.write("</TR>");
      }
      fw.write("</table><br><br>");
      fw.write("<HR/>");
    }
    fw.write("</body></html>");
    fw.flush();
    fw.close();

  }

  /**
   * A class copied from AnnotationDifferGUI to produce as identical
   * presentation of the results as possible. This class has been
   * modified a bit. It helps presenting annotation diff results in a
   * tabular format.
   * 
   * @author niraj
   */
  class DiffTable {

    // no of columns
    private final int COL_COUNT = 9;

    // various constants for different columns
    private final int COL_KEY_START = 0;

    private final int COL_KEY_END = 1;

    private final int COL_KEY_STRING = 2;

    private final int COL_KEY_FEATURES = 3;

    private final int COL_MATCH = 4;

    private final int COL_RES_START = 5;

    private final int COL_RES_END = 6;

    private final int COL_RES_STRING = 7;

    private final int COL_RES_FEATURES = 8;

    // colors for match, partial match, missing and false positives
    private final Color BG = Color.WHITE;

    private final Color PARTIALLY_CORRECT_BG = new Color(173, 215, 255);

    private final Color MISSING_BG = new Color(255, 173, 181);

    private final Color FALSE_POSITIVE_BG = new Color(255, 231, 173);

    // match labels for match, paritial match, missing and false
    // positives
    private String[] matchLabel = null;

    /**
     * Maximum number of characters for Key, Response and Features
     * columns.
     */
    private final int maxCellLength = 40;

    /**
     * Results of annotation diff
     */
    private List<AnnotationDiffer.Pairing> pairings;

    /**
     * Constructor
     * 
     * @param pairings results of annotation diff
     */
    public DiffTable(List<AnnotationDiffer.Pairing> pairings) {
      matchLabel = new String[5];
      matchLabel[AnnotationDiffer.CORRECT_TYPE] = "=";
      matchLabel[AnnotationDiffer.PARTIALLY_CORRECT_TYPE] = "~";
      matchLabel[AnnotationDiffer.MISSING_TYPE] = "-?";
      matchLabel[AnnotationDiffer.SPURIOUS_TYPE] = "?-";
      matchLabel[AnnotationDiffer.MISMATCH_TYPE] = "<>";

      this.pairings = pairings;
    }

    /**
     * number of rows
     * 
     * @return
     */
    public int getRowCount() {
      return pairings.size();
    }

    /**
     * number of columns
     * 
     * @return
     */
    public int getColumnCount() {
      return COL_COUNT;
    }

    /**
     * Column headers
     * 
     * @param column
     * @return
     */
    public String getColumnName(int column) {
      switch(column) {
        case COL_KEY_START:
          return "Start";
        case COL_KEY_END:
          return "End";
        case COL_KEY_STRING:
          return "Key";
        case COL_KEY_FEATURES:
          return "Features";
        case COL_MATCH:
          return "=?";
        case COL_RES_START:
          return "Start";
        case COL_RES_END:
          return "End";
        case COL_RES_STRING:
          return "Response";
        case COL_RES_FEATURES:
          return "Features";
        default:
          return "?";
      }
    }

    /**
     * Gets the background color of a cell
     * 
     * @param row
     * @param column
     * @return
     */
    public Color getBackgroundAt(int row, int column) {
      AnnotationDiffer.Pairing pairing = pairings.get(row);
      switch(pairing.getType()) {
        case (AnnotationDiffer.CORRECT_TYPE):
          return BG;
        case (AnnotationDiffer.PARTIALLY_CORRECT_TYPE):
          return PARTIALLY_CORRECT_BG;
        case (AnnotationDiffer.MISMATCH_TYPE):
          if(column < COL_MATCH)
            return MISSING_BG;
          else if(column > COL_MATCH)
            return FALSE_POSITIVE_BG;
          else return BG;
        case (AnnotationDiffer.MISSING_TYPE):
          return MISSING_BG;
        case (AnnotationDiffer.SPURIOUS_TYPE):
          return FALSE_POSITIVE_BG;
        default:
          return BG;
      }
    }

    /**
     * Gets a value of a particular cell
     * 
     * @param row
     * @param column
     * @return
     */
    public Object getValueAt(int row, int column) {

      // result at the given row
      AnnotationDiffer.Pairing pairing = pairings.get(row);

      // key and response annotation
      Annotation key = pairing.getKey();
      Annotation res = pairing.getResponse();

      // find out the column
      switch(column) {
        case COL_KEY_START:
          return key == null ? "" : key.getStartNode().getOffset().toString();
        case COL_KEY_END:
          return key == null ? "" : key.getEndNode().getOffset().toString();
        case COL_KEY_STRING:
          String keyStr = "";
          if(key != null && document != null) {
            keyStr = Utils.stringFor(document, key);
          }
          // cut annotated text in the middle if too long
          if(keyStr.length() > maxCellLength) {
            keyStr = keyStr.substring(0, maxCellLength / 2) + "..."
                    + keyStr.substring(keyStr.length() - (maxCellLength / 2));
          }
          // use special characters for newline, tab and space
          keyStr = keyStr.replaceAll("(?:\r?\n)|\r", "↓");
          keyStr = keyStr.replaceAll("\t", "→");
          keyStr = keyStr.replaceAll(" ", "·");
          return keyStr;
        case COL_KEY_FEATURES:
          return key == null ? "" : key.getFeatures().toString();
        case COL_MATCH:
          return matchLabel[pairing.getType()];
        case COL_RES_START:
          return res == null ? "" : res.getStartNode().getOffset().toString();
        case COL_RES_END:
          return res == null ? "" : res.getEndNode().getOffset().toString();
        case COL_RES_STRING:
          String resStr = "";
          if(res != null && document != null) {
            resStr = Utils.stringFor(document, res);
          }
          if(resStr.length() > maxCellLength) {
            resStr = resStr.substring(0, maxCellLength / 2) + "..."
                    + resStr.substring(resStr.length() - (maxCellLength / 2));
          }
          // use special characters for newline, tab and space
          resStr = resStr.replaceAll("(?:\r?\n)|\r", "↓");
          resStr = resStr.replaceAll("\t", "→");
          resStr = resStr.replaceAll(" ", "·");
          return resStr;
        case COL_RES_FEATURES:
          return res == null ? "" : res.getFeatures().toString();
        default:
          return "?";
      }
    }
  }
}
