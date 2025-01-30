/*
Author:Ipule Pipi
Email: ipipi2022@my.fit.edu
Course: CSE2010: Algorithms & Data Structures
Section: E1
Description of this file:  This files contains the implemenation of order handling system of a warehouse
*/

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
    boolean isAssigned;
    
    public CustomerOrder(String orderTime, String customer, int numBooks, int numElectronics) {
        this.orderTime = orderTime;
        this.customer = customer;
        this.numBooks = numBooks;
        this.numElectronics = numElectronics;
        this.isAssigned = false;
    }
    
    //check if of the same category
    public boolean isCompatibleFor(CustomerOrder other) {
        boolean thisHasBooks = numBooks > 0;
        boolean thisHasElec = numElectronics > 0;
        boolean otherHasBooks = other.numBooks > 0;
        boolean otherHasElec = other.numElectronics > 0;
    
        return (thisHasBooks == otherHasBooks && thisHasElec == otherHasElec) || 
               (thisHasBooks == otherHasBooks && !thisHasElec == !otherHasElec) || 
               (thisHasElec == otherHasElec && !thisHasBooks == !otherHasBooks);
    }
    
    
    public int getTotalItems() {
        return numBooks + numElectronics;
    }
    
    @Override
    public String toString() {
        return String.format("CustomerOrder %s %s %d %d", orderTime, customer, numBooks, numElectronics);
    }
}

class WorkerAssignment {
    String worker;
    List<CustomerOrder> orders;
    String startTime;
    String expectedCompletionTime;
    
    public WorkerAssignment(String worker, String startTime) {
        this.worker = worker;
        this.orders = new ArrayList<>();
        this.startTime = startTime;
    }
    
    public List<String> getCustomers() {
        List<String> customers = new ArrayList<>();
        for (CustomerOrder order : orders) {
            customers.add(order.customer);
        }
        return customers;
    }
    
    @Override
    public String toString() {
        return worker + ":" + String.join(",", getCustomers());
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
    private static SinglyLinkedList<CustomerOrder> orderQueue = new SinglyLinkedList<>();
    private static SinglyLinkedList<String> availableWorkers = new SinglyLinkedList<>();
    private static SinglyLinkedList<WorkerAssignment> activeAssignments = new SinglyLinkedList<>();
    private static List<Event> events = new ArrayList<>();
    private static int maxFulfillmentTime = 0;
    private static Map<String, WorkerAssignment> pendingBundles = new HashMap<>();
    private static Queue<CustomerOrder> unassignedOrders = new LinkedList<>();
    
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
            
            // Process any remaining bundles
            finalizePendingBundles(addMinutesToTime("2359", 0));
            
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
        String time = parts[1];
        
        // Process any bundles that should be completed by this time
        if (!command.equals("CustomerOrder")) {
            finalizePendingBundles(time);
        }
        
        switch (command) {
            case "CustomerOrder":
                if (parts.length == 5) {
                    CustomerOrder order = new CustomerOrder(
                        time, parts[2],
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4])
                    );
                    events.add(new Event(time, "CustomerOrder", 
                        String.format("%s %d %d", parts[2], order.numBooks, order.numElectronics)));
                    handleNewOrder(order);
                }
                break;
                
            case "PrintAvailableWorkerList":
                printAvailableWorkers(time);
                break;
                
            case "PrintWorkerAssignmentList":
                printWorkerAssignments(time);
                break;
                
            case "PrintMaxFulfillmentTime":
                events.add(new Event(time, "MaxFulfillmentTime", 
                    String.valueOf(maxFulfillmentTime)));
                break;
        }
    }
    
    private static void handleNewOrder(CustomerOrder newOrder) {
        if (availableWorkers.isEmpty()) {
            orderQueue.add(newOrder);
            return;
        }

        // If there are unassigned orders, this new order must go to the queue
        if (!unassignedOrders.isEmpty()) {
            unassignedOrders.offer(newOrder);
            return;
        }

        // Try to bundle with existing pending bundle
        boolean bundled = false;
        for (Map.Entry<String, WorkerAssignment> entry : pendingBundles.entrySet()) {
            WorkerAssignment bundle = entry.getValue();
            if (canAddToBundle(bundle, newOrder)) {
                bundle.orders.add(newOrder);
                bundled = true;
                break;
            }
        }
        
        if (!bundled) {
            // If we couldn't bundle the order, add it to unassigned orders
            if (availableWorkers.isEmpty()) {
                orderQueue.add(newOrder);
            } else {
                String worker = availableWorkers.removeFirst();
                WorkerAssignment newBundle = new WorkerAssignment(worker, newOrder.orderTime);
                newBundle.orders.add(newOrder);
                pendingBundles.put(worker, newBundle);
            }
        }
    }
    
    private static boolean canAddToBundle(WorkerAssignment bundle, CustomerOrder newOrder) {
        if (bundle.orders.isEmpty()) return true;
        
        CustomerOrder firstOrder = bundle.orders.get(0);
        if (!firstOrder.isCompatibleFor(newOrder)) return false;
        
        // Check if within 5-minute window
        if (timeDifference(firstOrder.orderTime, newOrder.orderTime) > 5) return false;
        
        // Check total items
        int totalItems = 0;
        for (CustomerOrder order : bundle.orders) {
            totalItems += order.getTotalItems();
        }
        return totalItems + newOrder.getTotalItems() <= 10;
    }
    
    private static void finalizePendingBundles(String currentTime) {
        List<String> completedWorkers = new ArrayList<>();
        
        for (Map.Entry<String, WorkerAssignment> entry : pendingBundles.entrySet()) {
            WorkerAssignment bundle = entry.getValue();
            if (!bundle.orders.isEmpty()) {
                CustomerOrder firstOrder = bundle.orders.get(0);
                CustomerOrder lastOrder = bundle.orders.get(bundle.orders.size() - 1);
                
                if (timeDifference(lastOrder.orderTime, currentTime) >= 5) {
                    // Calculate times
                    String assignmentTime = addMinutesToTime(lastOrder.orderTime, 5);
                    int processingTime = calculateProcessingTime(bundle.orders);
                    String completionTime = addMinutesToTime(assignmentTime, processingTime - 5);
                    
                    // Create events
                    String customers = String.join(",", bundle.getCustomers());
                    events.add(new Event(assignmentTime, "WorkerAssignment",
                        String.format("%s %s", entry.getKey(), customers)));
                    events.add(new Event(completionTime, "OrderCompletion", customers));
                    
                    // Update max fulfillment time
                    int fulfillmentTime = processingTime;
                    maxFulfillmentTime = Math.max(maxFulfillmentTime, fulfillmentTime);
                    
                    // Mark for completion
                    completedWorkers.add(entry.getKey());
                    availableWorkers.add(entry.getKey());
                }
            }
        }
        
        // Remove completed bundles
        for (String worker : completedWorkers) {
            pendingBundles.remove(worker);
        }

        // Process any unassigned orders after workers become available
        while (!orderQueue.isEmpty() && !availableWorkers.isEmpty()) {
            CustomerOrder queuedOrder = orderQueue.removeFirst();
            handleNewOrder(queuedOrder);
        }
    }
    
    private static int calculateProcessingTime(List<CustomerOrder> orders) {
        int time = 5; // Initial bundling window
        int totalBooks = 0;
        int totalElectronics = 0;
        
        for (CustomerOrder order : orders) {
            totalBooks += order.numBooks;
            totalElectronics += order.numElectronics;
        }
        
        if (totalBooks > 0 || totalElectronics > 0) {
            time += 5; // Travel to first category
            
            if (totalBooks > 0) {
                time += totalBooks; // 1 minute per book
            }
            
            if (totalElectronics > 0) {
                if (totalBooks > 0) time += 5; // Travel between categories
                time += totalElectronics; // 1 minute per electronic item
            }
            
            time += 5; // Return to packing station
        }
        
        return time;
    }
    
    private static int timeDifference(String time1, String time2) {
        int hour1 = Integer.parseInt(time1.substring(0, 2));
        int minute1 = Integer.parseInt(time1.substring(2));
        int hour2 = Integer.parseInt(time2.substring(0, 2));
        int minute2 = Integer.parseInt(time2.substring(2));
        
        return Math.abs((hour2 * 60 + minute2) - (hour1 * 60 + minute1));
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
        List<String> assignmentStrings = new ArrayList<>();
        
        for (WorkerAssignment assignment : pendingBundles.values()) {
            if (!assignment.orders.isEmpty()) {
                assignmentStrings.add(assignment.toString());
            }
        }
        
        events.add(new Event(time, "WorkerAssignmentList",
            String.join(" ", assignmentStrings)));
    }
}