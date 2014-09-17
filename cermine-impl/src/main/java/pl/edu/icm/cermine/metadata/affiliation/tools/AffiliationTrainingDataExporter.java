package pl.edu.icm.cermine.metadata.affiliation.tools;

import java.io.*;
import java.util.*;
import org.apache.commons.cli.*;
import org.jdom.JDOMException;
import org.xml.sax.InputSource;
import pl.edu.icm.cermine.exception.AnalysisException;
import pl.edu.icm.cermine.metadata.model.AffiliationToken;
import pl.edu.icm.cermine.metadata.model.DocumentAffiliation;
import pl.edu.icm.cermine.parsing.tools.GrmmUtils;
import pl.edu.icm.cermine.parsing.tools.TextClassifier;

/**
 * Class for converting affiliation features to GRMM file format. It reads
 * affiliations from an XML or text file, extracts their features and produces a
 * valid input for the ACRF model trainer. If the input file is an XML, it uses the
 * tags as labels.
 * 
 * This class may be used to generate the ACRF training file, as well as to check
 * whether the Java implementation exports affiliations to the
 * GRMM format in the exactly same way as our Python code in the prototype.
 * 
 * @author Bartosz Tarnawski
 */
public class AffiliationTrainingDataExporter {

	private static final AffiliationTokenizer tokenizer = new AffiliationTokenizer();
	private static AffiliationFeatureExtractor featureExtractor = null;

	private static final String DEFAULT_INPUT = "affiliations/javatests/affs-real-like.xml";
	private static final String DEFAULT_OUTPUT = "affiliations/javatests/features-actual-xml.txt";
	private static final String DEFAULT_WORDS = "affiliations/javatests/words-actual-xml.txt";
	private static final int DEFAULT_NEIGHBOR_THRESHOLD = 1;
	private static final int DEFAULT_RARE_THRESHOLD = 25;
	private static final String DEFAULT_INPUT_TYPE = "xml";

	private static void writeAffiliation(DocumentAffiliation affiliation, PrintWriter writer,
			int neighborThreshold) {
		writer.write(GrmmUtils.toGrmmInput(affiliation.getTokens(), neighborThreshold));
		writer.write("\n");
	}

	private static void writeCommonWords(List<String> commonWords, PrintWriter wordsWriter) {
		Collections.sort(commonWords);
		for (String word : commonWords) {
			wordsWriter.write(word + '\n');
		}
	}

	private static void addMockAffiliation(PrintWriter writer) {
		writer.write("TEXT ---- text\n\n");
	}
	
	private static List<String> getCommonWordsFromAffs(List<DocumentAffiliation> affiliations,
			int rareThreshold) {
		Map<String, Integer> occurences = new HashMap<String, Integer>(); 
        List<String> commonWords = new ArrayList<String>();
        
        for (DocumentAffiliation affiliation : affiliations) {
                for (AffiliationToken token: affiliation.getTokens()) {
                        String text = token.getText();
                        if (!TextClassifier.isWord(text)) {
                        	continue;
                        }
                        int wordOccurences = occurences.containsKey(text) ? occurences.get(text) : 0;
                        occurences.put(text, wordOccurences + 1);
                }
        }
        
        for (String key : occurences.keySet()) {
                if (occurences.get(key) > rareThreshold) {
                        commonWords.add(key);
                }
        }
        
        return commonWords;
	}
	
	private static List<String> loadCommonWords(BufferedReader wordsReader) throws IOException {
		List<String> words = new ArrayList<String>();
        String text;
        while ((text = wordsReader.readLine()) != null) {
                words.add(text);
        }
		return words;
	}
	
	private static List<DocumentAffiliation> loadAffiliationsFromTxt(BufferedReader reader)
			throws IOException {
		List<DocumentAffiliation> affiliations = new ArrayList<DocumentAffiliation>();
        String text;
        while ((text = reader.readLine()) != null) {
                DocumentAffiliation affiliation = new DocumentAffiliation(text);
                affiliation.setTokens(tokenizer.tokenize(affiliation.getRawText()));
                affiliations.add(affiliation);
        }
        return affiliations;
	}

	public static void main(String[] args) throws AnalysisException, ParseException, JDOMException {

		Options options = new Options();

		options.addOption("input", true, "input file (raw strings)");
		options.addOption("output", true, "output file (GRMM format)");
		options.addOption("common_words", true, "file with common (not-rare) words to generate");
		options.addOption("neighbor", true, "neighbor influence threshold");
		options.addOption("rare", true, "rare threshold");
		options.addOption("input_type", true, "xml or txt");
		options.addOption("add_mock_text", false, "should add TEXT");
		options.addOption("load_words", false, "read common words from file instead of writing them");

		CommandLineParser clParser = new GnuParser();
		CommandLine line = clParser.parse(options, args);

		String inputFileName = line.getOptionValue("input");
		String outputFileName = line.getOptionValue("output");
		String wordsFileName = line.getOptionValue("common_words");
		String neighborThresholdString = line.getOptionValue("neighbor");
		String rareThresholdString = line.getOptionValue("rare");
		String inputType = line.getOptionValue("input_type");

		int neighborThreshold = DEFAULT_NEIGHBOR_THRESHOLD;
		int rareThreshold = DEFAULT_RARE_THRESHOLD;
		boolean addMockText = false;
		boolean loadWords = false;

		if (line.hasOption("add_mock_text")) {
			addMockText = true;
		}

		if (line.hasOption("load_words")) {
			loadWords = true;
		}

		if (inputFileName == null) {
			inputFileName = DEFAULT_INPUT;
		}

		if (outputFileName == null) {
			outputFileName = DEFAULT_OUTPUT;
		}
		
		if (wordsFileName == null) {
			wordsFileName = DEFAULT_WORDS;
		}

		if (neighborThresholdString != null) {
			neighborThreshold = Integer.parseInt(neighborThresholdString);
		}

		if (rareThresholdString != null) {
			rareThreshold = Integer.parseInt(rareThresholdString);
		}

		if (inputType == null) {
			inputType = DEFAULT_INPUT_TYPE;
		}

		File file = new File(inputFileName);
		BufferedReader reader = null;
		BufferedReader wordsReader = null;
		PrintWriter writer = null;
		PrintWriter wordsWriter = null;
		NLMAffiliationExtractor nlmExtractor = new NLMAffiliationExtractor();

		try {
			writer = new PrintWriter(outputFileName, "UTF-8");
			
			if (loadWords) {
				wordsReader = new BufferedReader(new FileReader(wordsFileName));
			} else {
				wordsWriter = new PrintWriter(wordsFileName, "UTF-8");
			}
			
			List<DocumentAffiliation> affiliations = null;

			if (inputType.equals("txt")) {
				reader = new BufferedReader(new FileReader(file));
				affiliations = loadAffiliationsFromTxt(reader);
			} else if (inputType.equals("xml")) {
				FileInputStream is = new FileInputStream(file);
				InputSource source = new InputSource(is);
				affiliations = nlmExtractor.extractStrings(source);
			} else {
				throw new ParseException("Unknown input type: " + inputType);
			}
			
			List <String> commonWords;
			if (loadWords) {
				commonWords = loadCommonWords(wordsReader);
			} else {
				commonWords = getCommonWordsFromAffs(affiliations, rareThreshold);
			}
			
			featureExtractor = new AffiliationFeatureExtractor(commonWords);
			
			for (DocumentAffiliation affiliation : affiliations) {
				featureExtractor.calculateFeatures(affiliation);
				writeAffiliation(affiliation, writer, neighborThreshold);
			}
			
			if (addMockText) {
				addMockAffiliation(writer);
			}
			
			if (!loadWords) {
				writeCommonWords(commonWords, wordsWriter);
			}
			
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
				if (wordsReader != null) {
					wordsReader.close();
				}
				if (writer != null) {
					writer.close();
				}
				if (wordsWriter != null) {
					wordsWriter.close();
				}
			} catch (IOException e) {
				throw new RuntimeException("Can't close resources!");
			}
		}
	}
}