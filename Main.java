import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

public class Main {
    private static final int PORT = 8081;
    private static final String DATA_FILE = "parcels.txt";
    private static final List<Parcel> parcels = new ArrayList<>();
    private static int nextId = 1001;

    static class Parcel {
        String id, sender, receiver, phone, pickup, delivery, description;
        String status = "BOOKED", reason = "", redelivery = "";
        List<String> history = new ArrayList<>();
        Parcel(String sender, String receiver, String phone, String pickup, String delivery, String description) {
            this.id = "CR" + nextId++;
            this.sender = sender; this.receiver = receiver; this.phone = phone;
            this.pickup = pickup; this.delivery = delivery; this.description = description;
            history.add("Booked at " + LocalDateTime.now());
        }
    }

    public static void main(String[] args) throws Exception {
        loadData();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", Main::handleRequest);
        server.setExecutor(null);
        server.start();
        System.out.println("Courier Tracking Website is running at http://localhost:" + PORT);
    }

    private static void handleRequest(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (path.equals("/book") && method.equals("GET")) { send(ex, page(bookForm())); return; }
            if (path.equals("/add") && method.equals("POST")) { addParcel(ex); return; }
            if (path.equals("/track") && method.equals("GET")) { send(ex, page(trackPage(ex))); return; }
            if (path.equals("/register") && method.equals("GET")) { send(ex, page(registerForm())); return; }
            if (path.equals("/registered") && method.equals("POST")) { registerCustomer(ex); return; }
            if (path.equals("/search") && method.equals("GET")) { send(ex, page(searchPage(ex))); return; }
            if (path.equals("/dashboard") && method.equals("GET")) { send(ex, page(dashboard())); return; }
            if (path.equals("/admin") && method.equals("GET")) { send(ex, page(adminForm())); return; }
            if (path.equals("/update") && method.equals("POST")) { updateParcel(ex); return; }
            send(ex, page(home()));
        } catch (Exception e) {
            send(ex, page("<div class='card'><h2>Something went wrong</h2><p>" + esc(e.getMessage()) + "</p></div>"));
        }
    }

    private static String page(String content) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'>" +
            "<title>Courier & Parcel Tracking</title><style>" +
            "*{box-sizing:border-box}body{margin:0;font-family:Arial,sans-serif;background:#f2f5fb;color:#172554}" +
            "header{background:#142b55;color:white;padding:24px;text-align:center}nav{background:#203e70;padding:14px;text-align:center}" +
            "nav a{color:white;text-decoration:none;margin:8px;display:inline-block}main{max-width:1000px;margin:25px auto;padding:12px}" +
            ".card{background:white;padding:22px;margin-bottom:18px;border-radius:12px;box-shadow:0 3px 12px #0001}" +
            "input,select,textarea{width:100%;padding:11px;margin:7px 0 14px;border:1px solid #ccd5e5;border-radius:6px}" +
            "button,.btn{background:#2458a6;color:white;padding:11px 18px;border:0;border-radius:6px;cursor:pointer;text-decoration:none;display:inline-block}" +
            ".stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:12px}.stat{background:#e8efff;padding:16px;border-radius:9px;text-align:center}" +
            "table{width:100%;border-collapse:collapse;overflow-wrap:anywhere}td,th{padding:9px;border-bottom:1px solid #ddd;text-align:left}" +
            ".success{color:green}.muted{color:#64748b}footer{text-align:center;padding:20px;color:#64748b}" +
            "</style></head><body><header><h1>Courier & Parcel Tracking</h1><p>Fast • Safe • Reliable Delivery</p></header>" +
            "<nav><a href='/'>Home</a><a href='/book'>Book Parcel</a><a href='/track'>Track Parcel</a><a href='/register'>Register</a><a href='/dashboard'>Dashboard</a><a href='/search'>Search</a><a href='/admin'>Admin</a></nav>" +
            "<main>" + content + "</main><footer>Courier & Parcel Tracking System | Java Application</footer></body></html>";
    }

    private static String home() {
        return "<div class='card'><h2>Welcome to Courier Services</h2><p>Book, track and manage your parcels easily.</p>" +
            "<a class='btn' href='/book'>Book a Parcel</a> <a class='btn' href='/track'>Track a Parcel</a></div>" +
            "<div class='card'><h3>Our Services</h3><p>Easy parcel booking, live status updates, customer registration and parcel search.</p></div>";
    }

    private static String bookForm() {
        return "<div class='card'><h2>Book a Parcel</h2><form method='POST' action='/add'>" +
            field("Sender Name", "sender") + field("Receiver Name", "receiver") + field("Receiver Phone", "phone") +
            field("Pickup Address", "pickup") + field("Delivery Address", "delivery") + field("Parcel Description", "description") +
            "<button type='submit'>Book Parcel</button></form></div>";
    }

    private static String registerForm() {
        return "<div class='card'><h2>Customer Registration</h2><form method='POST' action='/registered'>" +
            field("Customer Name", "name") + field("Phone Number", "phone") + field("Address", "address") +
            "<button type='submit'>Register</button></form></div>";
    }

    private static String adminForm() {
        return "<div class='card'><h2>Update Parcel Status</h2><form method='POST' action='/update'>" +
            field("Tracking ID", "id") + "<label>Status</label><select name='status'>" +
            "<option>BOOKED</option><option>IN TRANSIT</option><option>DELIVERED</option><option>FAILED</option></select>" +
            field("Failure Reason (if failed)", "reason") + field("Redelivery Date and Time", "redelivery") +
            "<button type='submit'>Update Parcel</button></form></div>";
    }

    private static String field(String label, String name) {
        return "<label>" + label + "</label><input name='" + name + "' required>";
    }

    private static void addParcel(HttpExchange ex) throws IOException {
        Map<String,String> f = formData(ex);
        Parcel p = new Parcel(value(f,"sender"), value(f,"receiver"), value(f,"phone"), value(f,"pickup"), value(f,"delivery"), value(f,"description"));
        synchronized (parcels) { parcels.add(p); saveData(); }
        redirect(ex, "/track?id=" + urlEncode(p.id));
    }

    private static void registerCustomer(HttpExchange ex) throws IOException {
        Map<String,String> f = formData(ex);
        String line = value(f,"name") + " | " + value(f,"phone") + " | " + value(f,"address") + System.lineSeparator();
        Files.writeString(Path.of("customers.txt"), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        send(ex, page("<div class='card'><h2 class='success'>Customer registered successfully!</h2><a href='/'>Go Home</a></div>"));
    }

    private static String trackPage(HttpExchange ex) {
        String id = queryData(ex).getOrDefault("id", "");
        StringBuilder html = new StringBuilder("<div class='card'><h2>Track Your Parcel</h2><form method='GET' action='/track'><input name='id' placeholder='Enter Tracking ID' required><button>Track</button></form></div>");
        if (!id.isBlank()) {
            Parcel p = findParcel(id);
            if (p == null) html.append("<div class='card'>Parcel not found. Please check the tracking ID.</div>");
            else html.append(parcelDetails(p));
        }
        return html.toString();
    }

    private static String parcelDetails(Parcel p) {
        StringBuilder s = new StringBuilder("<div class='card'><h2>Parcel Details</h2>");
        s.append("<p><b>Tracking ID:</b> ").append(esc(p.id)).append("</p>");
        s.append("<p><b>Sender:</b> ").append(esc(p.sender)).append("</p>");
        s.append("<p><b>Receiver:</b> ").append(esc(p.receiver)).append("</p>");
        s.append("<p><b>Phone:</b> ").append(esc(p.phone)).append("</p>");
        s.append("<p><b>Pickup:</b> ").append(esc(p.pickup)).append("</p>");
        s.append("<p><b>Delivery:</b> ").append(esc(p.delivery)).append("</p>");
        s.append("<p><b>Description:</b> ").append(esc(p.description)).append("</p>");
        s.append("<h3>Status: ").append(esc(p.status)).append("</h3>");
        if (!p.reason.isBlank()) s.append("<p><b>Failure reason:</b> ").append(esc(p.reason)).append("</p>");
        if (!p.redelivery.isBlank()) s.append("<p><b>Redelivery:</b> ").append(esc(p.redelivery)).append("</p>");
        s.append("<h3>Tracking History</h3>");
        for (String h : p.history) s.append("<p>").append(esc(h)).append("</p>");
        return s.append("</div>").toString();
    }

    private static String searchPage(HttpExchange ex) {
        String q = queryData(ex).getOrDefault("q", "").trim();
        StringBuilder s = new StringBuilder("<div class='card'><h2>Search Parcels</h2><form method='GET' action='/search'><input name='q' placeholder='Tracking ID or receiver name' value='");
        s.append(esc(q)).append("'><button>Search</button></form></div>");
        if (!q.isEmpty()) {
            s.append("<div class='card'><h3>Search Results</h3>");
            boolean found = false;
            synchronized (parcels) {
                for (Parcel p : parcels) if (p.id.toLowerCase().contains(q.toLowerCase()) || p.receiver.toLowerCase().contains(q.toLowerCase())) {
                    found = true; s.append("<p><a href='/track?id=").append(urlEncode(p.id)).append("'>").append(esc(p.id)).append("</a> — ").append(esc(p.receiver)).append(" — ").append(esc(p.status)).append("</p>");
                }
            }
            if (!found) s.append("<p>No matching parcels found.</p>");
            s.append("</div>");
        }
        return s.toString();
    }

    private static String dashboard() {
        int booked=0, transit=0, delivered=0, failed=0;
        StringBuilder rows = new StringBuilder();
        synchronized (parcels) {
            for (Parcel p : parcels) {
                if (p.status.equals("BOOKED")) booked++;
                else if (p.status.equals("IN TRANSIT")) transit++;
                else if (p.status.equals("DELIVERED")) delivered++;
                else if (p.status.equals("FAILED")) failed++;
                rows.append("<tr><td>").append(esc(p.id)).append("</td><td>").append(esc(p.receiver)).append("</td><td>").append(esc(p.status)).append("</td></tr>");
            }
        }
        return "<h2>Admin Dashboard</h2><div class='stats'>" + stat("Customers", customerCount()) + stat("Total Parcels", parcels.size()) +
            stat("Booked",booked) + stat("In Transit",transit) + stat("Delivered",delivered) + stat("Failed",failed) +
            "</div><div class='card'><h3>All Parcels</h3><table><tr><th>Tracking ID</th><th>Receiver</th><th>Status</th></tr>" + rows + "</table></div>";
    }

    private static String stat(String name, int value) { return "<div class='stat'><h3>" + value + "</h3>" + name + "</div>"; }

    private static void updateParcel(HttpExchange ex) throws IOException {
        Map<String,String> f = formData(ex);
        Parcel p = findParcel(value(f,"id"));
        if (p == null) { send(ex, page("<div class='card'><h2>Parcel not found</h2><a href='/admin'>Try again</a></div>")); return; }
        String status = value(f,"status");
        if (!Arrays.asList("BOOKED","IN TRANSIT","DELIVERED","FAILED").contains(status)) status = "BOOKED";
        p.status = status; p.reason = value(f,"reason"); p.redelivery = value(f,"redelivery");
        p.history.add("Status changed to " + p.status + " at " + LocalDateTime.now());
        saveData();
        send(ex, page("<div class='card'><h2 class='success'>Parcel updated successfully!</h2><p>Tracking ID: " + esc(p.id) + "</p><a href='/track?id=" + urlEncode(p.id) + "'>View parcel</a></div>"));
    }

    private static Parcel findParcel(String id) {
        synchronized (parcels) { for (Parcel p : parcels) if (p.id.equalsIgnoreCase(id)) return p; }
        return null;
    }

    private static Map<String,String> formData(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseData(body);
    }
    private static Map<String,String> queryData(HttpExchange ex) {
        return parseData(ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery());
    }
    private static Map<String,String> parseData(String raw) {
        Map<String,String> result = new HashMap<>();
        for (String part : raw.split("&")) {
            if (part.isEmpty()) continue;
            String[] pair = part.split("=", 2);
            try { result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
    private static String value(Map<String,String> map, String key) { return map.getOrDefault(key, "").trim(); }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static String urlEncode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static void send(HttpExchange ex, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }
    private static void redirect(HttpExchange ex, String path) throws IOException {
        ex.getResponseHeaders().set("Location", path);
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }

    private static void saveData() {
        try (BufferedWriter w = Files.newBufferedWriter(Path.of(DATA_FILE), StandardCharsets.UTF_8)) {
            for (Parcel p : parcels) {
                w.write(String.join("\t", enc(p.id),enc(p.sender),enc(p.receiver),enc(p.phone),enc(p.pickup),enc(p.delivery),enc(p.description),enc(p.status),enc(p.reason),enc(p.redelivery)));
                w.newLine();
                for (String h : p.history) { w.write("H\t" + enc(p.id) + "\t" + enc(h)); w.newLine(); }
            }
        } catch (IOException e) { System.out.println("Could not save data: " + e.getMessage()); }
    }
    private static void loadData() {
        Path path = Path.of(DATA_FILE);
        if (!Files.exists(path)) return;
        Map<String,Parcel> byId = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String[] a = line.split("\t", -1);
                if (a.length == 3 && a[0].equals("H")) {
                    Parcel p = byId.get(dec(a[1])); if (p != null) p.history.add(dec(a[2]));
                } else if (a.length == 10) {
                    String id=dec(a[0]);
                    Parcel p = new Parcel(dec(a[1]),dec(a[2]),dec(a[3]),dec(a[4]),dec(a[5]),dec(a[6]));
                    p.id=id; p.status=dec(a[7]); p.reason=dec(a[8]); p.redelivery=dec(a[9]); p.history.clear();
                    byId.put(id,p);
                    try { nextId = Math.max(nextId, Integer.parseInt(id.substring(2)) + 1); } catch (Exception ignored) { }
                }
            }
            parcels.addAll(byId.values());
        } catch (IOException e) { System.out.println("Could not load saved data: " + e.getMessage()); }
    }
    private static String enc(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String s) { try { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8); } catch (IllegalArgumentException e) { return ""; } }
    private static int customerCount() {
        try { if (!Files.exists(Path.of("customers.txt"))) return 0; try (java.util.stream.Stream<String> lines = Files.lines(Path.of("customers.txt"))) { return (int) lines.count(); } }
        catch (IOException e) { return 0; }
    }
}