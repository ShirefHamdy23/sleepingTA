package sleepingta;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class SleepingTA extends Application {

    private Semaphore taSemaphore;
    private Semaphore studentSemaphore;
    private Semaphore assistanceSemaphore;

    private Queue<Integer> waitingStudents = new LinkedList<>();
    private Map<Integer, Integer> studentChairMap = new HashMap<>();
    private boolean allStudentsAssisted = false;

    private CountDownLatch studentsLatch;
    private CountDownLatch teachersLatch;

    private int numChairs;
    private long startTime;

    private TextField studentsTextField;
    private TextField taTextField;
    private TextField chairsTextField;
    private ProgressBar progressBar;
    private TextArea textArea;
    private Button startButton;
    private Button stopButton;
    private TextField taWorkingTextField;
    private TextField taSleepingTextField;
    private TextField stdOnChairTextField;
    private TextField stdOutsideTextField;

    private Scene scene;  // Added scene variable

    private volatile boolean stopSimulation = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Sleeping TA Simulation");

        // Load the FXML file
        FXMLLoader loader = new FXMLLoader(getClass().getResource(".\\sleepingTA.fxml"));

        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Set up the scene
        scene = new Scene(root);
        primaryStage.setScene(scene);

        // Initialize controls
        studentsTextField = (TextField) scene.lookup("#studentsTextField");
        taTextField = (TextField) scene.lookup("#taTextField");
        chairsTextField = (TextField) scene.lookup("#chairsTextField");
        progressBar = (ProgressBar) scene.lookup("#progressBar");
        textArea = (TextArea) scene.lookup("#textArea");
        startButton = (Button) scene.lookup("#startButton");
        stopButton = (Button) scene.lookup("#stopButton");
        taWorkingTextField = (TextField) scene.lookup("#taWorking");
        taSleepingTextField = (TextField) scene.lookup("#taSleeping");
        stdOnChairTextField = (TextField) scene.lookup("#stdOnChair");
        stdOutsideTextField = (TextField) scene.lookup("#stdOutside");

        // Configure button actions
        startButton.setOnAction(event -> {
            resetSimulation();
            startSimulation();
        });

        stopButton.setOnAction(event -> stopSimulation());

        // Show the GUI
        primaryStage.show();
    }

    private void initializeTextFields() {


        taWorkingTextField.textProperty().bind(Bindings.createStringBinding(() ->
                        String.valueOf(waitingStudents.size()), FXCollections.observableArrayList(waitingStudents)));
        taSleepingTextField.textProperty().bind(Bindings.createStringBinding(() ->
                        String.valueOf(numChairs - waitingStudents.size()), FXCollections.observableArrayList(waitingStudents)));
        stdOnChairTextField.textProperty().bind(Bindings.createStringBinding(() ->
                        String.valueOf(waitingStudents.size()), FXCollections.observableArrayList(waitingStudents)));
        stdOutsideTextField.textProperty().bind(Bindings.createStringBinding(() ->
                        String.valueOf(Integer.parseInt(studentsTextField.getText()) - waitingStudents.size()),
                FXCollections.observableArrayList(waitingStudents)));

        // Set up other text field bindings as needed
    }

    private void resetTextFields() {
        if (taWorkingTextField != null) {
            taWorkingTextField.clear();
        }
        if (taSleepingTextField != null) {
            taSleepingTextField.clear();
        }
        if (stdOnChairTextField != null) {
            stdOnChairTextField.clear();
        }
        if (stdOutsideTextField != null) {
            stdOutsideTextField.clear();
        }
    }

    private void resetSimulation() {
        progressBar.setProgress(0);
        textArea.clear();
        stopSimulation = false;
        resetTextFields();
    }

    private void stopSimulation() {
        stopSimulation = true;
        textArea.appendText("Simulation stopped.\n-------------------------\n");
        taSemaphore.release();
        Thread.currentThread().interrupt();
    }

    private void startSimulation() {
        int numStudents = Integer.parseInt(studentsTextField.getText());
        int numTeachers = Integer.parseInt(taTextField.getText());
        numChairs = Integer.parseInt(chairsTextField.getText());

        startTime = System.currentTimeMillis();

        taSemaphore = new Semaphore(0);
        studentSemaphore = new Semaphore(1);
        assistanceSemaphore = new Semaphore(0);

        studentsLatch = new CountDownLatch(numStudents);
        teachersLatch = new CountDownLatch(numTeachers);

        Thread taThread = new Thread(this::teachingAssistant);
        taThread.setDaemon(true);
        taThread.start();

        Thread[] studentThreads = new Thread[numStudents];
        Thread[] teacherThreads = new Thread[numTeachers];

        for (int i = 0; i < numStudents; i++) {
            final int studentId = i + 1;
            studentThreads[i] = new Thread(() -> student(studentId));
            studentThreads[i].start();
        }

        for (int i = 0; i < numTeachers; i++) {
            final int teacherId = i + 1;
            teacherThreads[i] = new Thread(() -> teacher(teacherId));
            teacherThreads[i].start();
        }

        Task<Void> progressTask = new Task<>() {
            @Override
            protected Void call() {
                while (!stopSimulation && studentsLatch.getCount() > 0) {
                    double progress = 1 - ((double) studentsLatch.getCount() / numStudents);
                    Platform.runLater(() -> {
                        progressBar.setProgress(progress);
                        taWorkingTextField.setText(String.valueOf(waitingStudents.size()));
                        taSleepingTextField.setText(String.valueOf(numChairs - waitingStudents.size()));
                        stdOnChairTextField.setText(String.valueOf(waitingStudents.size()));
                        stdOutsideTextField.setText(String.valueOf(Integer.parseInt(studentsTextField.getText()) - waitingStudents.size()));
                    });
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return null;
            }
        };

        new Thread(progressTask).start();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    studentsLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            allStudentsAssisted = true;
            taSemaphore.release();
            try {
                taThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            long totalSeconds = totalTime / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;

            String totalTimeMessage = "Total time taken: " + minutes + " minutes and " + seconds + " seconds";
            String simulationEndedMessage = "All students have left. Simulation ended.\n-------------------------\n";

            Platform.runLater(() -> {
                updateTextArea(totalTimeMessage);
                updateTextArea(simulationEndedMessage);
                progressBar.setProgress(1.0);
                resetTextFields();
            });
        });

        new Thread(task).start();
    }

    private void teachingAssistant() {
        while (!stopSimulation) {
            try {
                taSemaphore.acquire();
                if (waitingStudents.isEmpty() && allStudentsAssisted) {
                    Platform.runLater(() -> updateTextArea("TA goes home."));
                    break;
                }
                Integer studentId = waitingStudents.poll();
                if (studentId != null) {
                    int chair = studentChairMap.get(studentId);
                    Platform.runLater(() -> updateTextArea("TA starts helping Student " + studentId + " on Chair " + chair));
                    Thread.sleep(500);
                    Platform.runLater(() -> updateTextArea("TA finishes helping Student " + studentId));
                    studentsLatch.countDown();
                    assistanceSemaphore.release();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void student(int studentId) {
        Random rand = new Random();
        while (!stopSimulation) {
            try {
                Thread.sleep(rand.nextInt(500) + 100);

                studentSemaphore.acquire();
                if (waitingStudents.size() < numChairs) {
                    waitingStudents.offer(studentId);
                    int chair = waitingStudents.size();
                    studentChairMap.put(studentId, chair);
                    Platform.runLater(() -> updateTextArea("Student " + studentId + " arrives and waits for TA's help on Chair " + chair));
                    printChairStatus();
                    studentSemaphore.release();
                    taSemaphore.release();
                    assistanceSemaphore.acquire();
                    Platform.runLater(() -> updateTextArea("Student " + studentId + " leaves after getting help."));
                    studentsLatch.countDown();
                    break;
                } else {
                    Platform.runLater(() -> updateTextArea("No available chairs for Student " + studentId + ". Student waits."));
                    printChairStatus();
                    studentSemaphore.release();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void teacher(int teacherId) {
        Random rand = new Random();
        while (!stopSimulation) {
            try {
                Thread.sleep(rand.nextInt(500) + 100);

                studentSemaphore.acquire();
                if (!waitingStudents.isEmpty()) {
                    int studentId = waitingStudents.poll();
                    int chair = studentChairMap.get(studentId);
                    Platform.runLater(() -> updateTextArea("Teacher " + teacherId + " is assisting Student " + studentId + " on Chair " + chair));
                    studentSemaphore.release();
                    Thread.sleep(500);
                    Platform.runLater(() -> updateTextArea("Teacher " + teacherId + " finishes assisting Student " + studentId));
                    assistanceSemaphore.release();
                    break;
                } else {
                    studentSemaphore.release();
                }
            } catch (InterruptedException e) {
                Platform.runLater(() -> updateTextArea("Teacher " + teacherId + " goes home."));
                teachersLatch.countDown();
                break;
            }
        }
    }

    private void printChairStatus() {
        StringBuilder status = new StringBuilder("Chair status: [");
        for (int i = 1; i <= numChairs; i++) {
            if (waitingStudents.contains(i)) {
                status.append("Student ").append(getKeyByValue(studentChairMap, i));
            } else {
                status.append("Empty");
            }
            if (i < numChairs) {
                status.append(", ");
            }
        }
        status.append("]");
        Platform.runLater(() -> updateTextArea(status.toString()));
    }

    private void updateTextArea(String message) {
        Platform.runLater(() -> textArea.appendText(message + "\n"));
    }

    private Integer getKeyByValue(Map<Integer, Integer> map, int value) {
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
