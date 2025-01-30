import java.io.*;
import java.util.*;

class Event implements Comparable<Event> {
    String time;
    String type;
    String content;
    
    public Event(String time, String type, String content) {
        this.time = time;
        this.type = type;
        this.content = content;
    }
    
    @Override
    public String toString() {
        return String.format("%s %s %s", type, time, content);
    }
    
    @Override
    public int compareTo(Event other) {
        return this.time.compareTo(other.time);
    }
}

class CustomerOrder {
    String orderTime;
    String customer;
    int numBooks;
    int numElectronics;
    
    public CustomerOrder(String orderTime, String customer, int numBooks, int numElectronics) {
        this.orderTime = orderTime;
        this.customer = customer;
        this.numBooks = numBooks;
        this.numElectronics = numElectronics;
    }
    
    @Override
    public String toString() {
        return String.format("CustomerOrder %s %s %d %d", orderTime, customer, numBooks, numElectronics);
    }
}

class WorkerAssignment {
    String worker;
    List<String> customers;
    String startTime;
    String expectedCompletionTime;
    
    public WorkerAssignment(String worker, String startTime) {
        this.worker = worker;
        this.customers = new ArrayList<>();
        this.startTime = startTime;
    }
    
    @Override
    public String toString() {
        return worker + ":" + String.join(",", customers);
    }
}

class SinglyLinkedList<T> {
    private class Node {
        T data;
        Node next;
        
        Node(T data) {
            this.data = data;
            this.next = null;
        }
    }
    
    private Node head;
    private Node tail;
    
    public void add(T data) {
        Node newNode = new Node(data);
        if (head == null) {
            head = tail = newNode;
        } else {
            tail.next = newNode;
            tail = newNode;
        }
    }
    
    public T removeFirst() {
        if (head == null) return null;
        T data = head.data;
        head = head.next;
        if (head == null) tail = null;
        return data;
    }
    
    public boolean isEmpty() {
        return head == null;
    }
    
    public List<T> toList() {
        List<T> list = new ArrayList<>();
        Node current = head;
        while (current != null) {
            list.add(current.data);
            current = current.next;
        }
        return list;
    }
}

public class HW1 {
    private static SinglyLinkedList<CustomerOrder> orderList = new SinglyLinkedList<>();
    private static SinglyLinkedList<String> availableWorkers = new SinglyLinkedList<>();
    private static SinglyLinkedList<WorkerAssignment> workerAssignments = new SinglyLinkedList<>();
    private static int maxFulfillmentTime = 0;
    private static List<Event> events = new ArrayList<>();
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java HW1 <input_file>");
            System.exit(1);
        }
        
        // Initialize available workers
        String[] workers = {"Alice", "Bob", "Carol", "David", "Emily"};
        for (String worker : workers) {
            availableWorkers.add(worker);
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(args[0]))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processCommand(line.trim());
            }
            // Print all events in chronological order
            Collections.sort(events);
            for (Event event : events) {
                System.out.println(event);
            }
        } catch (IOException e) {
            System.err.println("Error reading input file: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processCommand(String line) {
        String[] parts = line.split("\\s+");
        String command = parts[0];
        
        switch (command) {
            case "CustomerOrder":
                if (parts.length == 5) {
                    CustomerOrder order = new CustomerOrder(
                        parts[1], parts[2],
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4])
                    );
                    events.add(new Event(parts[1], "CustomerOrder", 
                        String.format("%s %d %d", parts[2], Integer.parseInt(parts[3]), Integer.parseInt(parts[4]))));
                    processCustomerOrder(order);
                }
                break;
                
            case "PrintAvailableWorkerList":
                if (parts.length == 2) {
                    printAvailableWorkers(parts[1]);
                }
                break;
                
            case "PrintWorkerAssignmentList":
                if (parts.length == 2) {
                    printWorkerAssignments(parts[1]);
                }
                break;
                
            case "PrintMaxFulfillmentTime":
                if (parts.length == 2) {
                    events.add(new Event(parts[1], "MaxFulfillmentTime",
                        String.valueOf(maxFulfillmentTime)));
                }
                break;
        }
    }
    
    private static void processCustomerOrder(CustomerOrder order) {
        if (availableWorkers.isEmpty()) {
            orderList.add(order);
            return;
        }
        
        String worker = availableWorkers.removeFirst();
        WorkerAssignment assignment = new WorkerAssignment(worker, order.orderTime);
        assignment.customers.add(order.customer);
        
        // Calculate processing time
        int processingTime = calculateProcessingTime(order);
        String completionTime = addMinutesToTime(order.orderTime, processingTime);
        assignment.expectedCompletionTime = completionTime;
        
        // Update max fulfillment time
        maxFulfillmentTime = Math.max(maxFulfillmentTime, processingTime);
        
        // Add worker assignment event (5-minute bundling window)
        String assignmentTime = addMinutesToTime(order.orderTime, 5);
        events.add(new Event(assignmentTime, "WorkerAssignment",
            String.format("%s %s", worker, String.join(",", assignment.customers))));
            
        workerAssignments.add(assignment);
        
        // Add order completion event
        events.add(new Event(completionTime, "OrderCompletion",
            String.join(",", assignment.customers)));

        // Add worker back to available list after completion
        availableWorkers.add(worker);

        // Remove from worker assignments
        List<WorkerAssignment> assignments = workerAssignments.toList();
        workerAssignments = new SinglyLinkedList<>();
        for (WorkerAssignment a : assignments) {
            if (!a.worker.equals(worker)) {
                workerAssignments.add(a);
            }
        }
    }
    
    private static int calculateProcessingTime(CustomerOrder order) {
        int time = 5; // Initial bundling window
        
        if (order.numBooks > 0 || order.numElectronics > 0) {
            time += 5; // Travel to first category
            
            if (order.numBooks > 0) {
                time += order.numBooks; // 1 minute per book
            }
            
            if (order.numElectronics > 0) {
                if (order.numBooks > 0) time += 5; // Travel between categories
                time += order.numElectronics; // 1 minute per electronic item
            }
            
            time += 5; // Return to packing station
        }
        
        return time;
    }
    
    private static String addMinutesToTime(String time, int minutes) {
        int hour = Integer.parseInt(time.substring(0, 2));
        int minute = Integer.parseInt(time.substring(2));
        
        minute += minutes;
        hour += minute / 60;
        minute %= 60;
        hour %= 24;
        
        return String.format("%02d%02d", hour, minute);
    }
    
    private static void printAvailableWorkers(String time) {
        List<String> workers = availableWorkers.toList();
        events.add(new Event(time, "AvailableWorkerList",
            String.join(" ", workers)));
    }
    
    private static void printWorkerAssignments(String time) {
        List<WorkerAssignment> assignments = workerAssignments.toList();
        List<String> assignmentStrings = new ArrayList<>();
        
        for (WorkerAssignment assignment : assignments) {
            assignmentStrings.add(assignment.toString());
        }
        
        events.add(new Event(time, "WorkerAssignmentList",
            String.join(" ", assignmentStrings)));
    }
}