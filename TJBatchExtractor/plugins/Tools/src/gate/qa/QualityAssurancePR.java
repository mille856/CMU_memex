package gate.qa;

import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.ontology.InvalidURIException;
import gate.util.AnnotationDiffer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * This PR has the same functionality as the Quality Assurance GUI. If
 * added to a corpus pipeline, it executes on the very last document -
 * making sure that the statistics are calculated on the entire corpus.
 * User can provide various run-time parameters to configure the PR. One
 * of the parameters is the output directory where the results are
 * stored. The PR produces two main files - one for the corpus
 * statistics and one for the documents statistics. In case of the
 * latter, each document in the output file is linked with the output of
 * annotationDiff utility containing annotation by annotation comparison
 * for every annotation type specified in annotationTypes parameter.
 * 
 * @author niraj
 * 
 */
@CreoleResource(name = "Quality Assurance PR", comment = "The Quality Assurance PR provides a functionality of"
        + " the Corpus QA Tool in GATE Developer")
public class QualityAssurancePR extends AbstractLanguageAnalyser implements
                                                                ProcessingResource {

  /**
   * Key annotation set name
   */
  private String keyASName;

  /**
   * response annotation set name
   */
  private String responseASName;

  /**
   * types of annotations to use
   */
  private List<String> annotationTypes;

  /**
   * features to use
   */
  private List<String> featureNames;

  /**
   * which measure
   */
  private Measure measure;

  /**
   * measure string generated from the selected measure
   */
  private String measureString;

  /**
   * folder where the output files need to be stored
   */
  private URL outputFolderUrl;

  /**
   * number formatter
   */
  protected NumberFormat f = NumberFormat.getInstance(Locale.ENGLISH);

  /** Initialise this resource, and return it. */
  public Resource init() throws ResourceInstantiationException {
    f.setMaximumFractionDigits(2); // format used for all decimal values
    f.setMinimumFractionDigits(2);
    return this;
  } // init()

  /**
   * The execute method
   */
  public void execute() throws ExecutionException {

    // the corpus cannot be null or empty
    if(corpus == null || corpus.size() == 0) {
      throw new ExecutionException("Corpus cannot be null or empty");
    }

    // similarly user must provide annotation types that they want to
    // compare
    if(annotationTypes == null || annotationTypes.isEmpty())
      throw new ExecutionException(
              "Please provide at least one annotation type to compare");

    // also a measure to use for computation
    if(measure == null) {
      throw new ExecutionException("No measure selected");
    }

    // check if we are processing the last document in the corpus
    Document lastDocument = (Document)corpus.get(corpus.size() - 1);
    if(lastDocument != document) {
      return;
    }

    // user must provide an output folder. This is where the files with
    // results
    // are exported.
    File outputFolder = null;
    try {
      outputFolder = new File(outputFolderUrl.toURI());
      if(!outputFolder.exists()) {
        if(!outputFolder.mkdirs()) {
          throw new ExecutionException("Could not create a folder : "
                  + outputFolder.getAbsolutePath());
        }
      }
      else {
        if(!outputFolder.isDirectory()) {
          throw new ExecutionException("Invalid directory : "
                  + outputFolder.getAbsolutePath());
        }
      }
    }
    catch(InvalidURIException iue) {
      throw new ExecutionException(iue);
    }
    catch(URISyntaxException use) {
      throw new ExecutionException(use);
    }

    // conversion of Measure parameter to string is needed to reuse
    // the login of QA tool. I have made the minimum changes needed to
    // reuse the code of QA tool.
    // WARNING: please do not change the code below, unless you are sure
    // what you are doing.
    switch(measure) {
      case F1_STRICT:
        measureString = "f1.0-strict";
        break;
      case F1_LENIENT:
        measureString = "f1.0-lenient";
        break;
      case F1_AVERAGE:
        measureString = "f1.0-average";
        break;
      case F05_STRICT:
        measureString = "f0.5-strict";
        break;
      case F05_LENIENT:
        measureString = "f0.5-lenient";
        break;
      case F05_AVERAGE:
        measureString = "f0.5-average";
        break;
    }

    // storing differs for individual documents
    List<Map<String, AnnotationDiffer>> differsByDocThenType = new ArrayList<Map<String, AnnotationDiffer>>();

    // storing document names
    List<String> documentNames = new ArrayList<String>();

    // for each document
    for(int row = 0; row < corpus.size(); row++) {

      // if the document is loaded in GATE
      boolean documentWasLoaded = corpus.isDocumentLoaded(row);

      // obtain the document from corpus
      Document document = (Document)corpus.get(row);
      documentNames.add(document.getName());

      // get annotations from the selected key and response annotation
      // sets
      AnnotationSet keys = keyASName == null || keyASName.trim().length() == 0
              ? document.getAnnotations()
              : document.getAnnotations(keyASName);
      AnnotationSet responses = responseASName == null
              || responseASName.trim().length() == 0 ? document
              .getAnnotations() : document.getAnnotations(responseASName);

      // a differ object for each type
      Map<String, AnnotationDiffer> differsByType = new HashMap<String, AnnotationDiffer>();

      // differ doesn't have any method to access results letter, so
      // storing
      // results in a temporary map
      Map<AnnotationDiffer, List<AnnotationDiffer.Pairing>> pairingsByDiffer = new HashMap<AnnotationDiffer, List<AnnotationDiffer.Pairing>>();

      // configure differs for one annotation type at a time
      for(String type : annotationTypes) {
        AnnotationSet keyAS = keys.get(type);
        AnnotationSet respAS = responses.get(type);

        // perform annotation diff for this type
        AnnotationDiffer differ = new AnnotationDiffer();
        Set<String> featuresSet = new HashSet<String>();
        if(featureNames != null && !featureNames.isEmpty()) {
          featuresSet.addAll(featureNames);
        }
        differ.setSignificantFeaturesSet(new HashSet<String>(featuresSet));
        List<AnnotationDiffer.Pairing> pairings = differ.calculateDiff(keyAS,
                respAS);

        // store results in temporary maps
        pairingsByDiffer.put(differ, pairings);
        differsByType.put(type, differ);
      }

      // differs object would be needed later to produce summary
      differsByDocThenType.add(differsByType);

      // using diff Exporter to produce a single html file containing
      // annotation
      // diff results for all annotation types
      AnnotationDiffExporter diffExporter = new AnnotationDiffExporter(
              pairingsByDiffer, document, getKeyASName(), getResponseASName());

      try {
        diffExporter.export(getDiffResultsExportFile(document.getName()));
      }
      catch(IOException e) {
        throw new ExecutionException(e);
      }
      finally {
        // unload the document if it wasnt loaded earlier
        if(!documentWasLoaded) {
          corpus.unloadDocument(document);
          Factory.deleteResource(document);
        }
      }
    }

    // calculate corpus statistics for the different annotation types
    String corpusStats = calculateCorpusStats(differsByDocThenType);

    // calculate statistic for each document
    String documentStats = calculateDocumentStats(documentNames,
            differsByDocThenType);

    // html output
    String corpusOutput = "<html><body><b> Corpus Statistics</b><br>"
            + corpusStats + "<br></body></html>";
    String documentOutput = "<html><body><b> Document Statistics</b><br>"
            + documentStats + "<br></body></html>";

    // writer object
    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(new FileWriter(new File(outputFolder,
              "corpus-stats.html")));
      bw.write(corpusOutput);
      bw.close();
      bw = new BufferedWriter(new FileWriter(new File(outputFolder,
              "document-stats.html")));
      bw.write(documentOutput);
    }
    catch(IOException ioe) {
      throw new ExecutionException(ioe);
    }
    finally {
      if(bw != null) {
        try {
          bw.close();
        }
        catch(IOException e) {
          throw new ExecutionException(e);
        }
      }
    }
  }

  /**
   * Generates a file name to export annotation diff results to.
   */
  protected File getDiffResultsExportFile(String documentName) {
    // document Name - keyASName - responseASNAme - diff.html
    String fileName = documentName.replaceAll("[ ]+", "_") + "-"
            + getKeyASName() + "-" + getResponseASName() + "-diff.html";
    return new File(getOutputFolderUrl().getFile(), fileName);
  }

  /**
   * Given an instance of Differ and the measure, this method returns
   * the value for the given measure. It returns three values,
   * precision, recall and f-measure
   */
  private double[] getMeasureValue(AnnotationDiffer differ, String measure) {
    double[] vals = new double[3];
    // recall
    if(measure.endsWith("strict")) {
      vals[0] = differ.getRecallStrict();
    }
    else if(measure.endsWith("lenient")) {
      vals[0] = differ.getRecallLenient();
    }
    else {
      vals[0] = differ.getRecallAverage();
    }

    // precision
    if(measure.endsWith("strict")) {
      vals[1] = differ.getPrecisionStrict();
    }
    else if(measure.endsWith("lenient")) {
      vals[1] = differ.getPrecisionLenient();
    }
    else {
      vals[1] = differ.getPrecisionAverage();
    }

    // f-measure
    double beta = Double.valueOf(measure.substring(1, measure.indexOf('-')));
    if(measure.endsWith("strict")) {
      vals[2] = differ.getFMeasureStrict(beta);
    }
    else if(measure.endsWith("lenient")) {
      vals[2] = differ.getFMeasureLenient(beta);
    }
    else {
      vals[2] = differ.getFMeasureAverage(beta);
    }
    return vals;
  }

  /**
   * Calculating stats for each doc in the corpus
   */
  private String calculateDocumentStats(List<String> documentNames,
          List<Map<String, AnnotationDiffer>> differsByDocThenType)
          throws ExecutionException {

    // document names + two rows for macro and micro averages
    String[] docNames = new String[differsByDocThenType.size() + 2];

    // column names for the html table
    String[] colnames = {"Document Name", "Match", "Only in Key",
        "Only in Response", "Overlap", "Rec.B/A", "Prec.B/A", measureString};

    // last two rows for macro and micro averages
    double[][] vals = new double[differsByDocThenType.size() + 2][7];

    // one document at a time
    for(int rowIndex = 0; rowIndex < differsByDocThenType.size(); rowIndex++) {
      Map<String, AnnotationDiffer> differsByType = differsByDocThenType
              .get(rowIndex);

      // creating a differ for this doc using all differs of different
      // annotation types in this document
      AnnotationDiffer differ = new AnnotationDiffer(differsByType.values());

      // collecting stats
      docNames[rowIndex] = documentNames.get(rowIndex);
      vals[rowIndex][0] = differ.getCorrectMatches();
      vals[rowIndex][1] = differ.getMissing();
      vals[rowIndex][2] = differ.getSpurious();
      vals[rowIndex][3] = differ.getPartiallyCorrectMatches();

      double[] tempvals = getMeasureValue(differ, measureString);
      vals[rowIndex][4] = tempvals[0];
      vals[rowIndex][5] = tempvals[1];
      vals[rowIndex][6] = tempvals[2];
    }

    // calculate macro average and reuse the values calculated earlier
    int i = differsByDocThenType.size();
    docNames[i] = "Macro Summary";

    for(int row = 0; row < differsByDocThenType.size(); row++) {
      vals[i][4] += vals[row][4];
      vals[i][5] += vals[row][5];
      vals[i][6] += vals[row][6];
    }
    vals[i][4] = vals[i][4] / differsByDocThenType.size();
    vals[i][5] = vals[i][5] / differsByDocThenType.size();
    vals[i][6] = vals[i][6] / differsByDocThenType.size();

    // calculating micro average
    i++;
    docNames[i] = "Micro Summary";
    for(int row = 0; row < differsByDocThenType.size(); row++) {
      vals[i][0] += vals[row][0];
      vals[i][1] += vals[row][1];
      vals[i][2] += vals[row][2];
      vals[i][3] += vals[row][3];
    }

    ArrayList<AnnotationDiffer> differs = new ArrayList<AnnotationDiffer>();
    for(Map<String, AnnotationDiffer> differsByType : differsByDocThenType) {
      differs.addAll(differsByType.values());
    }
    AnnotationDiffer differ = new AnnotationDiffer(differs);
    double[] tempvals = getMeasureValue(differ, measureString);
    vals[i][4] = tempvals[0];
    vals[i][5] = tempvals[1];
    vals[i][6] = tempvals[2];

    // finally populate the html table with column names and values
    String[] exportFileNames = new String[docNames.length - 2];
    for(int j = 0; j < exportFileNames.length; j++) {
      exportFileNames[j] = getDiffResultsExportFile(docNames[j]).getName();
    }

    // coverting results into an html table
    return toHtmlTable(docNames, exportFileNames, vals, colnames);
  }

  /**
   * Produces the html table with parameters
   */
  private String toHtmlTable(String[] firstCol, String[] anchorsOnFirstCol,
          double[][] vals, String[] columnNames) {
    StringBuffer buffer = new StringBuffer();
    buffer.append("<table>\n");
    buffer.append("\t<tr>\n");

    // add column titles
    for(String s : columnNames) {
      buffer.append("\t\t<td>\n");
      buffer.append(s);
      buffer.append("\t\t</td>\n");
    }
    buffer.append("\t</tr>\n");

    // doc name followed by values as calculated earlier
    for(int i = 0; i < firstCol.length; i++) {
      buffer.append("\t<tr>\n");
      buffer.append("\t\t<td>\n");

      // if there are links to individual annotation diff results
      // available
      // link doc names to respective file names
      boolean endAnchor = false;
      if(anchorsOnFirstCol != null && i < anchorsOnFirstCol.length) {
        buffer.append("<a href=\"" + anchorsOnFirstCol[i] + "\">");
        endAnchor = true;
      }
      buffer.append(firstCol[i]);
      if(endAnchor) {
        buffer.append("</a>");
      }
      buffer.append("\t\t</td>\n");

      double[] colvals = vals[i];
      for(double v : colvals) {
        buffer.append("\t\t<td>\n");
        buffer.append(f.format(v));
        buffer.append("\t\t</td>\n");
      }
      buffer.append("\t</tr>\n");
    }
    buffer.append("</table>");
    return buffer.toString();
  }

  /**
   * Calculating corpus statistics for each type
   */
  private String calculateCorpusStats(
          List<Map<String, AnnotationDiffer>> differsByDocThenType) {

    // annotation types found in the document
    String[] typesNames = new String[annotationTypes.size() + 2];

    // column names used in the html table
    String[] colnames = {"Annotation Type", "Match", "Only in Key",
        "Only in Response", "Overlap", "Rec.B/A", "Prec.B/A", measureString};

    // last two rows used for macro and micro averages
    double[][] vals = new double[annotationTypes.size() + 2][7];

    // one annotation type at a time
    for(int rowIndex = 0; rowIndex < annotationTypes.size(); rowIndex++) {
      // get the counts and measures for the current document/row
      String type = annotationTypes.get(rowIndex);

      // by iterating over all documents, obtain differs created for the
      // type
      // under consideration
      ArrayList<AnnotationDiffer> differs = new ArrayList<AnnotationDiffer>();
      for(Map<String, AnnotationDiffer> differsByType : differsByDocThenType) {
        differs.add(differsByType.get(type));
      }

      // calculate various stats
      AnnotationDiffer differ = new AnnotationDiffer(differs);
      typesNames[rowIndex] = type;
      vals[rowIndex][0] = differ.getCorrectMatches();
      vals[rowIndex][1] = differ.getMissing();
      vals[rowIndex][2] = differ.getSpurious();
      vals[rowIndex][3] = differ.getPartiallyCorrectMatches();

      double[] tempvals = getMeasureValue(differ, measureString);
      vals[rowIndex][4] = tempvals[0];
      vals[rowIndex][5] = tempvals[1];
      vals[rowIndex][6] = tempvals[2];
    }

    // macro summary
    int i = annotationTypes.size();
    typesNames[i] = "Macro Summary";

    for(int row = 0; row < annotationTypes.size(); row++) {
      vals[i][4] += vals[row][4];
      vals[i][5] += vals[row][5];
      vals[i][6] += vals[row][6];
    }
    vals[i][4] = vals[i][4] / annotationTypes.size();
    vals[i][5] = vals[i][5] / annotationTypes.size();
    vals[i][6] = vals[i][6] / annotationTypes.size();

    // micro summary
    i++;
    typesNames[i] = "Micro Summary";
    for(int row = 0; row < annotationTypes.size(); row++) {
      vals[i][0] += vals[row][0];
      vals[i][1] += vals[row][1];
      vals[i][2] += vals[row][2];
      vals[i][3] += vals[row][3];
    }

    ArrayList<AnnotationDiffer> differs = new ArrayList<AnnotationDiffer>();
    for(Map<String, AnnotationDiffer> differsByType : differsByDocThenType) {
      differs.addAll(differsByType.values());
    }
    AnnotationDiffer differ = new AnnotationDiffer(differs);

    double[] tempvals = getMeasureValue(differ, measureString);
    vals[i][4] = tempvals[0];
    vals[i][5] = tempvals[1];
    vals[i][6] = tempvals[2];

    // populate the html table with values
    return toHtmlTable(typesNames, null, vals, colnames);
  }

  /**
   * Returns the key annotation set name provided by the user
   */
  public String getKeyASName() {
    return keyASName;
  }

  /**
   * Sets the key annotation set name
   */
  @RunTime
  @Optional
  @CreoleParameter(defaultValue = "Key")
  public void setKeyASName(String keyASName) {
    this.keyASName = keyASName;
  }

  /**
   * Returns the response annotation set name provided by the user
   */
  public String getResponseASName() {
    return responseASName;
  }

  /**
   * sets the response annotation set name
   */
  @Optional
  @RunTime
  @CreoleParameter(defaultValue = "")
  public void setResponseASName(String responseASName) {
    this.responseASName = responseASName;
  }

  /**
   * Annotation types for which the stats should be calculated
   */
  public List<String> getAnnotationTypes() {
    return annotationTypes;
  }

  /**
   * Annotation types for which the stats should be calculated
   */
  @RunTime
  @CreoleParameter
  public void setAnnotationTypes(List<String> annotationTypes) {
    this.annotationTypes = annotationTypes;
  }

  /**
   * Features names for which the stats should be calculated
   */
  public List<String> getFeatureNames() {
    return featureNames;
  }

  /**
   * Features names for which the stats should be calculated
   */
  @RunTime
  @Optional
  @CreoleParameter
  public void setFeatureNames(List<String> featureNames) {
    this.featureNames = featureNames;
  }

  /**
   * Measure to use for stats calculation
   */
  public Measure getMeasure() {
    return measure;
  }

  /**
   * Measure to use for stats calculation
   */
  @RunTime
  @CreoleParameter
  public void setMeasure(Measure measure) {
    this.measure = measure;
  }

  /**
   * URL of the folder to store output files into
   */
  public URL getOutputFolderUrl() {
    return outputFolderUrl;
  }

  /**
   * URL of the folder to store output files into
   */
  @RunTime
  @CreoleParameter(suffixes = "html")
  public void setOutputFolderUrl(URL outputFolderUrl) {
    this.outputFolderUrl = outputFolderUrl;
  }
}
