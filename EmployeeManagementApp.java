import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * EmployeeManagementApp – single-file CLI (Java 22)
 * ------------------------------------------------
 *  • Age > 18 validation (add & update)
 *  • Unique-ID check – adding a duplicate exits with a message
 *  • CSV auto-load / auto-save, with header row
 *  • Payroll stats, bulk/individual raises, role filter, sort options
 */
public class EmployeeManagementApp {

    /* ── Program-wide state ────────────────────────────────── */
    private static final Scanner SC = new Scanner(System.in);
    private static final EmployeeManagementSystem EMS = new EmployeeManagementSystem();
    private static final Path CSV_PATH =
            Path.of(System.getProperty("user.dir"), "employees.csv");

    /* ── Entry point ───────────────────────────────────────── */
    public static void main(String[] args) {
        if (Files.exists(CSV_PATH)) {
            try {
                EMS.loadCsv(CSV_PATH);
                ok("Auto-loaded " + EMS.getAll().size() + " employees from " + CSV_PATH);
            } catch (IOException e) {
                warn("Failed to load CSV: " + e.getMessage());
            }
        } else {
            System.out.println("No previous data found. Starting fresh.");
        }

        int choice;
        do {
            showMenu();
            choice = readInt("Choice: ");
            try {
                switch (choice) {
                    case 1  -> addEmployee();
                    case 2  -> removeEmployee();
                    case 3  -> listAll();
                    case 4  -> searchById();
                    case 5  -> listByRole();
                    case 6  -> updateEmployee();
                    case 7  -> raiseSalary();
                    case 8  -> payrollStats();
                    case 9  -> saveCsv();
                    case 10 -> loadCsv();
                    case 11 -> sortAndShow();
                    case 12 -> System.out.println("Bye 👋");
                    default -> warn("Invalid option.");
                }
            } catch (Exception ex) {
                warn(ex.getMessage());
            }
            System.out.println();
        } while (choice != 12);

        SC.close();
    }

    /* ── CRUD helpers ─────────────────────────────────────── */
    private static void addEmployee() {
        int id = readInt("ID: ");
        if (EMS.getById(id).isPresent()) {
            warn("ID already exists. Program will exit.");
            System.exit(1);
        }
        String name = readLine("Name: ");
        int age = readInt("Age (>18): ");
        if (age <= 18) {
            warn("Age must be above 18. Exiting.");
            System.exit(1);
        }
        double salary = readDouble("Salary: ");
        Role role = pickRole();
        LocalDate join = readDate("Date of joining (yyyy-MM-dd): ");

        EMS.add(new Employee(id, name, age, salary, role, join));
        ok("Added.");
    }

    private static void removeEmployee() {
        if (EMS.remove(readInt("ID to remove: ")))
            ok("Removed.");
        else
            warn("No such ID.");
    }

    private static void listAll() {
        header();
        EMS.getAll().forEach(System.out::println);
    }

    private static void searchById() {
        EMS.getById(readInt("ID to search: "))
           .ifPresentOrElse(System.out::println, () -> warn("Not found."));
    }

    private static void listByRole() {
        Role r = pickRole();
        header();
        EMS.filter(e -> e.getRole() == r).forEach(System.out::println);
    }

    private static void updateEmployee() {
        int id = readInt("ID to update: ");
        EMS.getById(id).ifPresentOrElse(emp -> {
            System.out.println("Current → " + emp);
            String name = readLine("New name (blank = skip): ");
            if (!name.isBlank()) emp.setName(name);

            String ageStr = readLine("New age (blank = skip): ");
            if (!ageStr.isBlank()) {
                int newAge = Integer.parseInt(ageStr);
                if (newAge <= 18) {
                    warn("Age must be above 18.");
                    return;
                }
                emp.setAge(newAge);
            }

            String salStr = readLine("New salary (blank = skip): ");
            if (!salStr.isBlank()) emp.setSalary(Double.parseDouble(salStr));

            if (yesNo("Change role? (y/n): "))
                emp.setRole(pickRole());

            ok("Updated.");
        }, () -> warn("Not found."));
    }

    /* ── Salary helpers ───────────────────────────────────── */
    private static void raiseSalary() {
        if (yesNo("Bulk raise? (y/n): ")) {
            double pct = readDouble("% raise: ");
            Role filter = yesNo("Only one role? (y/n): ") ? pickRole() : null;
            EMS.raiseAll(pct, filter);
            ok("Bulk raise applied.");
        } else {
            int id  = readInt("Employee ID: ");
            double pct = readDouble("% raise: ");
            if (EMS.raise(id, pct)) ok("Raised.");
            else warn("No such ID.");
        }
    }

    /* ── Analytics & CSV ─────────────────────────────────── */
    private static void payrollStats() {
        System.out.printf("Total payroll ₹ %.2f%n", EMS.totalPayroll());
        EMS.averageByRole().forEach(
            (r, avg) -> System.out.printf("Avg %-7s ₹ %.2f%n", r, avg));
    }

    private static void saveCsv() throws IOException {
        EMS.saveCsv(CSV_PATH);
        ok("Saved to " + CSV_PATH);
    }

    private static void loadCsv() throws IOException {
        EMS.loadCsv(CSV_PATH);
        ok("Loaded " + EMS.getAll().size() + " employees.");
    }

    private static void sortAndShow() {
        System.out.println("""
            Sort by:
             1. Name
             2. Salary
             3. Join-Date""");
        int o = readInt("Option: ");
        Comparator<Employee> cmp = switch (o) {
            case 1 -> Comparator.comparing(Employee::getName, String.CASE_INSENSITIVE_ORDER);
            case 2 -> Comparator.comparingDouble(Employee::getSalary).reversed();
            case 3 -> Comparator.comparing(Employee::getJoinDate);
            default -> { warn("Bad option."); yield null; }
        };
        if (cmp != null) {
            header();
            EMS.sort(cmp).forEach(System.out::println);
        }
    }

    /* ── UI helpers ───────────────────────────────────────── */
    private static void showMenu() {
        System.out.println("""
            ╔════════════════════════════════════════╗
            ║ 1 Add      2 Remove    3 List all      ║
            ║ 4 Search   5 List role 6 Update        ║
            ║ 7 Raise    8 Payroll   9 Save CSV      ║
            ║10 Load CSV 11 Sort     12 Exit         ║
            ╚════════════════════════════════════════╝
        """);
    }

    private static void header() {
        System.out.println("ID  | Name            |Ag|  Salary | Role   | Joined     |Exp");
        System.out.println("----+-----------------+--+---------+--------+------------+---");
    }

    /* ── Tiny IO helpers ─────────────────────────────────── */
    private static void ok(String m)   { System.out.println("✅ " + m); }
    private static void warn(String m) { System.out.println("❌ " + m); }
    private static int    readInt(String p){ return Integer.parseInt(readLine(p)); }
    private static double readDouble(String p){ return Double.parseDouble(readLine(p)); }
    private static String readLine(String p){ System.out.print(p); return SC.nextLine().trim(); }
    private static LocalDate readDate(String p){ return LocalDate.parse(readLine(p)); }
    private static boolean yesNo(String p){ return readLine(p).equalsIgnoreCase("y"); }
    private static Role pickRole(){
        System.out.println("Role: 1.INTERN  2.FRESHER  3.SENIOR");
        return Role.fromInt(readInt("Choose: "));
    }
}

/* ── Domain enum ─────────────────────────────────────────── */
enum Role {
    INTERN, FRESHER, SENIOR;
    public static Role fromInt(int o) {
        return switch (o) {
            case 1 -> INTERN;
            case 2 -> FRESHER;
            case 3 -> SENIOR;
            default -> throw new IllegalArgumentException("Bad role option: " + o);
        };
    }
}

/* ── Domain model ───────────────────────────────────────── */
class Employee {
    private final int id;
    private String name;
    private int age;
    private double salary;
    private Role role;
    private final LocalDate joinDate;

    Employee(int id, String name, int age,
             double salary, Role role, LocalDate join) {
        if (age <= 18)  throw new IllegalArgumentException("Age ≤ 18");
        if (join.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Join date in future");

        this.id = id; this.name = name; this.age = age;
        this.salary = salary; this.role = role; this.joinDate = join;
    }

    /* getters */
    int getId(){ return id; }
    String getName(){ return name; }
    int getAge(){ return age; }
    double getSalary(){ return salary; }
    Role getRole(){ return role; }
    LocalDate getJoinDate(){ return joinDate; }
    int getExperienceYears(){
        return Period.between(joinDate, LocalDate.now()).getYears();
    }

    /* mutators */
    void setName(String name){ this.name = name; }
    void setAge(int age){
        if (age <= 18) throw new IllegalArgumentException("Age ≤ 18");
        this.age = age;
    }
    void setRole(Role r){ this.role = r; }
    void setSalary(double s){ this.salary = s; }
    void raiseSalary(double pct){ this.salary *= 1 + pct/100.0; }

    /* CSV helpers */
    String toCsv(){
        return String.join(",", String.valueOf(id), name,
                String.valueOf(age), String.valueOf(salary),
                role.name(), joinDate.toString());
    }
    static Employee fromCsv(String line){
        String[] p = line.split(",", -1);
        return new Employee(
                Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]),
                Double.parseDouble(p[3]), Role.valueOf(p[4]),
                LocalDate.parse(p[5]));
    }

    @Override
    public String toString() {
        return String.format("%-3d | %-15s | %2d | %8.2f | %-7s | %s | %2dy",
                id, name, age, salary, role,
                joinDate, getExperienceYears());
    }
}

/* ── Service layer ───────────────────────────────────────── */
class EmployeeManagementSystem {
    private static final String CSV_HEADER = "id,name,age,salary,role,joinDate";
    private final List<Employee> employees = new ArrayList<>();

    /* CRUD */
    boolean add(Employee e){ return getById(e.getId()).isEmpty() && employees.add(e); }
    boolean remove(int id){ return employees.removeIf(e -> e.getId() == id); }
    Optional<Employee> getById(int id){
        return employees.stream().filter(e -> e.getId() == id).findFirst();
    }

    /* Raises */
    boolean raise(int id, double pct){
        return getById(id).map(e -> { e.raiseSalary(pct); return true; }).orElse(false);
    }
    void raiseAll(double pct, Role filter){
        employees.stream().filter(e -> filter == null || e.getRole() == filter)
                 .forEach(e -> e.raiseSalary(pct));
    }

    /* Queries */
    List<Employee> getAll(){ return employees; }
    List<Employee> filter(Predicate<Employee> p){ return employees.stream().filter(p).toList(); }
    List<Employee> sort(Comparator<Employee> c){ return employees.stream().sorted(c).toList(); }
    double totalPayroll(){ return employees.stream().mapToDouble(Employee::getSalary).sum(); }

    Map<Role, Double> averageByRole(){
        return Arrays.stream(Role.values()).collect(Collectors.toMap(
                r -> r,
                r -> employees.stream().filter(e -> e.getRole() == r)
                              .mapToDouble(Employee::getSalary)
                              .average().orElse(0.0)
        ));
    }

    /* CSV persistence */
    void saveCsv(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(CSV_HEADER);
        lines.addAll(employees.stream().map(Employee::toCsv).toList());
        Files.write(file, lines,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    void loadCsv(Path file) throws IOException {
        employees.clear();
        List<String> lines = Files.readAllLines(file);
        lines.stream().skip(1)                   // skip header
             .filter(l -> !l.isBlank())
             .map(Employee::fromCsv)
             .forEach(employees::add);
    }
}