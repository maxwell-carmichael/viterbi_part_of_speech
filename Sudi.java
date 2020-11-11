import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Sudi {
    private final Map<String, Map<String, Double>> observationGraph;  // Observation -> (nextState -> log(p)) - observation scores
    private final Map<String, Map<String, Double>> transitionPOSGraph; // currState -> (nextState -> log(p)) - transition scores
    private static final String start = "#";    // start part of speech
    private static final double U = -50;    // missing part of speech

    /**
     * Constructor for hard-coding
     */
    private Sudi() {
        observationGraph = new HashMap<String, Map<String, Double>>();
        transitionPOSGraph = new HashMap<>();
    }

    /**
     * Constructor for training Sudi
     */
    public Sudi(String trainingSentencesFilePath, String trainingTagsFilePath) {
        observationGraph = new HashMap<String, Map<String, Double>>();
        transitionPOSGraph = new HashMap<>();
        train(trainingSentencesFilePath, trainingTagsFilePath);
    }

    /**
     * Takes a sentence separated by spaces and returns a list of parts of speech corresponding to each word.
     * @param input string to be interpreted
     * @return ordered list of parts of speech corresponding to each word in input
     */
    public String[] dissect(String input) {
        String[] words = input.split(" ");

        List<Map<String, String>> backtrace = new ArrayList<>();
        String[] rPartsOfSpeech = new String[words.length]; // array of corresponding parts of speech to return

        Set<String> currStates = new HashSet<>();   // holds currStates
        Map<String, Double> currScores = new HashMap<>();   // holds currScores for each currState

        currStates.add(start);
        currScores.put(start, 0.0);

        // block to generate most likely part of speech backtrace
        for (int i = 0; i < words.length; i++) {    // for each observation
            Set<String> nextStates = new HashSet<>();   // nextStates for observation i
            Map<String, Double> nextScores = new HashMap<>();   // nextScores for nextStates
            backtrace.add(i, new HashMap<>());  // add hashMap at observation i from nextState (at i) to currState (at i-1)

            for (String currState : currStates) {   // for each state at observation i-1
                // part of speech should exist, but in the case it doesn't:
                if (!transitionPOSGraph.containsKey(currState)) {
                    System.out.println("Part of speech doesn't exist! Empty list returned.");
                    return rPartsOfSpeech;
                }

                for (String nextState : transitionPOSGraph.get(currState).keySet()) {   // for each nextState to transition to from currState
                    nextStates.add(nextState);

                    // if-else to determine the score for the observation for nextState
                    double observationScore;
                    if (!observationGraph.containsKey(words[i].toLowerCase())) {
                        observationScore = U;
                    }
                    else if (observationGraph.get(words[i].toLowerCase()).containsKey(nextState)) {
                        observationScore = observationGraph.get(words[i].toLowerCase()).get(nextState);
                    }
                    else observationScore = U;

                    // nextScore = currScore for currState + transition score for currState to nextState + observation score for word with nextState
                    double nextScore = currScores.get(currState) + transitionPOSGraph.get(currState).get(nextState) + observationScore;
                    // add score to nextScores if it is not there or if it is greater than the current score
                    if (!nextScores.containsKey(nextState) || nextScore > nextScores.get(nextState)) {
                        nextScores.put(nextState, nextScore);
                        backtrace.get(i).put(nextState, currState);
                    }
                }
            }

            // update it for the next observation
            currStates = nextStates;
            currScores = nextScores;
        }

        // determines state for last observation with highest score (closest to 0)
        String bestFinalState = null;
        for (String currState : currStates) {
            if (bestFinalState == null || currScores.get(currState) > currScores.get(bestFinalState)) {
                bestFinalState = currState;
            }
        }

        // block to return ordered list of parts of speech for each word in input
        // add from last word of input to first word
        String currState = bestFinalState;
        for (int i = words.length - 1; i >= 0; i--) {
            rPartsOfSpeech[i] = currState;
            currState = backtrace.get(i).get(currState);    // get the previous state
        }

        return rPartsOfSpeech;
    }

    /**
     * Method called directly from the constructor to train Sudi based on sentences and tags files
     */
    private void train(String trainingSentencesFilePath, String trainingTagsFilePath) {

        // First, count the number of parts of speech and sentence starts that appear in trainingTagsFilePath.
        Map<String, Integer> partOfSpeechCount = new HashMap<>();

        BufferedReader input1 = null;   // file to read: trainingTagsFilePath

        // Open the file
        try {
            input1 = new BufferedReader(new FileReader(trainingTagsFilePath));
        }
        catch(FileNotFoundException e) {    // If cannot open, return empty map
            System.err.println("Cannot open file.\n" + e.getMessage());
        }

        // Read the file
        try {
            // add start to part of speech count
            partOfSpeechCount.put(start, 0);

            String line = input1.readLine();
            while (line != null) {
                // given each line is a sentence, increment start whenever a new line is read
                partOfSpeechCount.put(start, partOfSpeechCount.get(start) + 1);

                // increment parts of speech by how many times each appears in line, adding the part of speech
                // if it is not yet added to the parts of speech counter
                String[] partsOfSpeechInLine= line.split(" ");
                for (String POS : partsOfSpeechInLine) {
                    if (!partOfSpeechCount.containsKey(POS)) {
                        partOfSpeechCount.put(POS, 1);
                    }
                    else partOfSpeechCount.put(POS, partOfSpeechCount.get(POS) + 1);
                }

                line = input1.readLine();        // read next line
            }
        }

        catch (IOException e) {
            System.out.println("IO error while reading.\n" + e.getMessage());
        }

        // Close the file, if possible
        try {
            input1.close();
        }
        catch (IOException e) {
            System.err.println("Cannot close file.\n" + e.getMessage());
        }




        // Second, update transitionPOSGraph so it takes the form: currPOS -> (possible next POS -> total count of transition).
        // Also, simultaneously update observationPOSGraph so it takes the form: observation -> (possible POS -> total count of possibility).
        BufferedReader tags = null;   // file to read: trainingTagsFilePath (again)
        BufferedReader obs = null;    // file to read: trainingSentencesFilePath

        // Open tags
        try {
            tags = new BufferedReader(new FileReader(trainingTagsFilePath));
        }
        catch(FileNotFoundException e) {    // If cannot open, return empty map
            System.err.println("Cannot open file.\n" + e.getMessage());
        }

        // Open obs
        try {
            obs = new BufferedReader(new FileReader(trainingSentencesFilePath));
        }
        catch(FileNotFoundException e) {    // If cannot open, return empty map
            System.err.println("Cannot open file.\n" + e.getMessage());
        }

        // Read tags and obs simultaneously
        try {
            String tagsLine = tags.readLine();
            String obsLine = obs.readLine();

            // add start to the transitions graph
            transitionPOSGraph.put(start, new HashMap<String, Double>());

            // loop through the entire files
            while (tagsLine != null && obsLine != null) {
                String[] partsOfSpeechInLine = tagsLine.split(" ");
                String[] observationsInLine = obsLine.split(" ");

                // if the sentences do not have the same number of words:
                if (partsOfSpeechInLine.length != observationsInLine.length) {
                    System.err.println("training files not same format!");
                    break;
                }

                // run through every part of speech in the sentence
                for (int i = 0; i < partsOfSpeechInLine.length; i++) {
                    String currPOS = partsOfSpeechInLine[i];    // part of speech at loc i
                    String currObs = observationsInLine[i];     // word at loc i
                    String prevPOS;                             // part of speech at loc i-1

                    // handle the case at i=0 where the transition is from start to the first word.
                    if (i == 0) {
                        prevPOS = start;
                    }
                    else {
                        prevPOS = partsOfSpeechInLine[i-1];
                    }

                    // Add currPOS and currObs to both graphs if not there yet
                    if (!transitionPOSGraph.containsKey(currPOS)) {
                        transitionPOSGraph.put(currPOS, new HashMap<>());
                    }
                    if (!observationGraph.containsKey(currObs)) {
                        observationGraph.put(currObs, new HashMap<>());
                    }

                    // Increment count of transition by 1 (transition refers to prevPOS -> currPOS).
                    if (transitionPOSGraph.get(prevPOS).containsKey(currPOS)) {
                        transitionPOSGraph.get(prevPOS).put(currPOS, transitionPOSGraph.get(prevPOS).get(currPOS) + 1.0);
                    }
                    // Else initialize the existence of the transition.
                    else transitionPOSGraph.get(prevPOS).put(currPOS, 1.0);

                    // Increment count of observation's part of speech by 1
                    if (observationGraph.get(currObs).containsKey(currPOS)) {
                        observationGraph.get(currObs).put(currPOS, observationGraph.get(currObs).get(currPOS) + 1.0);
                    }
                    // Else initialize the existence of the possible part of speech for the observation
                    else observationGraph.get(currObs).put(currPOS, 1.0);
                }

                tagsLine = tags.readLine();        // read next line
                obsLine = obs.readLine();
            }
        }

        catch (IOException e) {
            System.out.println("IO error while reading.\n" + e.getMessage());
        }

        // Close the tags, if possible
        try {
            tags.close();
        }
        catch (IOException e) {
            System.err.println("Cannot close file.\n" + e.getMessage());
        }

        // Close the obs, if possible
        try {
            obs.close();
        }
        catch (IOException e) {
            System.err.println("Cannot close file.\n" + e.getMessage());
        }



        // Thirdly, and lastly, generate the probabilities (normalize the counts)
        // And take the natural log of them (to prevent probabilities from getting too small later)

        // for each part of speech to transition from in transitionPOSGraph
        for (String currPOS : transitionPOSGraph.keySet()) {
            int currPOSCount = partOfSpeechCount.get(currPOS);

            // for each part of speech which currPOS transitions to
            for (String nextPOS : transitionPOSGraph.get(currPOS).keySet()) {
                // replace the count with the ln of the probability of transition
                transitionPOSGraph.get(currPOS).put(nextPOS, Math.log(transitionPOSGraph.get(currPOS).get(nextPOS)/currPOSCount));
            }
        }

        // NOTE: here is where I realized I could have switched observationGraph to part of speech -> (obs -> score)
        // However, the way I wrote my dissect method made the way I did it fine.

        // for each observation to part of speech
        for (String currObs : observationGraph.keySet()) {
            // for each part of speech which currPOS transitions to
            for (String currPOS : observationGraph.get(currObs).keySet()) {
                int currPOSCount = partOfSpeechCount.get(currPOS);  // total number of current part of speech

                // replace the count with the ln of the probability of transition
                observationGraph.get(currObs).put(currPOS, Math.log(observationGraph.get(currObs).get(currPOS)/currPOSCount));
            }
        }
    }

    /**
     * Static method to take the array generated in dissect and make it easier to read when associated
     * with the current sentence in the console
     * @param partsOfSpeech dissected originalSentence
     */
    private static void printLabeledSentence(String originalSentence, String[] partsOfSpeech) {
        String[] originalWords = originalSentence.split(" ");
        // Only label if there is a part of speech for each word in the original sentence
        if (originalWords.length != partsOfSpeech.length) System.out.println("Unable to label!");
        else {
            String output = "";

            // construct output string with format: wor0(part of speech) word1(part of speech)...
            for (int i = 0; i < originalWords.length; i++) {
                output += originalWords[i] + "(" + partsOfSpeech[i] + ") ";
            }

            System.out.println(output);
        }
    }

    /**
     * Method called on Sudi to allow a user to input their own sentences in the console and Sudi to print
     * her best guess at the parts of speech for each word
     */
    public void inputConsole() {
        Scanner in = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.println("test a sentence>");
            String line = in.nextLine();                // line to read
            printLabeledSentence(line, dissect(line));  // prints Sudi's best guess
        }
    }

    /**
     * After training Sudi, you can prevent it with a sentence test file and a tags test file.
     * It will tell you how many tags it got correct out of the total number of words in the sentence file.
     */
    public void printCorrectness(String testingSentencesFilePath, String testingTagsFilePath) {
        int numCorrect = 0;
        int numTotal = 0;

        BufferedReader tags = null;   // file to read: testingTagsFilePath (again)
        BufferedReader obs = null;    // file to read: testingSentencesFilePath

        // Open tags
        try {
            tags = new BufferedReader(new FileReader(testingTagsFilePath));
        }
        catch(FileNotFoundException e) {    // If cannot open, return empty map
            System.err.println("Cannot open file.\n" + e.getMessage());
        }

        // Open obs
        try {
            obs = new BufferedReader(new FileReader(testingSentencesFilePath));
        }
        catch(FileNotFoundException e) {    // If cannot open, return empty map
            System.err.println("Cannot open file.\n" + e.getMessage());
        }

        // Read the files
        try {
            String tagsLine = tags.readLine();
            String obsLine = obs.readLine();

            // loop through the entire files
            while (tagsLine != null && obsLine != null) {
                // for each part of speech in the list
                String[] guessedPartsOfSpeechInLine = dissect(obsLine);
                String[] partsOfSpeechInLine = tagsLine.split(" ");;

                // Ensure files are in valid format
                if (partsOfSpeechInLine.length != guessedPartsOfSpeechInLine.length) {
                    System.err.println("test files not same format!");
                    break;
                }

                // for each word in the line
                for (int i=0; i < partsOfSpeechInLine.length; i++) {
                    // increment total number of tests by 1
                    numTotal++;
                    // if part of speech matches the guessed part of speech, increment correct by 1
                    if (guessedPartsOfSpeechInLine[i].equals(partsOfSpeechInLine[i])) numCorrect++;
                }

                tagsLine = tags.readLine();        // read next line
                obsLine = obs.readLine();
            }
        }

        catch (IOException e) {
            System.out.println("IO error while reading.\n" + e.getMessage());
        }

        // Close the tags, if possible
        try {
            tags.close();
        }
        catch (IOException e) {
            System.err.println("Cannot close file.\n" + e.getMessage());
        }

        // Close the obs, if possible
        try {
            obs.close();
        }
        catch (IOException e) {
            System.err.println("Cannot close file.\n" + e.getMessage());
        }

        // PRINT RESULTS!:
        System.out.println(numCorrect + " parts of speech correct out of " + numTotal + " total.");
    }

    public static void main(String[] args) {
        // create and train Sudi
        v = new Sudi("brown-train-sentences.txt", "brown-train-tags.txt");
        // see how Sudi did
        v.printCorrectness("brown-test-sentences.txt", "brown-test-tags.txt");
        // allow user to test
        v.inputConsole();
    }
}
