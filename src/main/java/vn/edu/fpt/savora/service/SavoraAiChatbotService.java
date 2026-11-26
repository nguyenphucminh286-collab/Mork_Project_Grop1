package vn.edu.fpt.savora.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.savora.entity.DiningTable;
import vn.edu.fpt.savora.entity.Dish;
import vn.edu.fpt.savora.entity.Voucher;
import vn.edu.fpt.savora.enums.DishStatus;
import vn.edu.fpt.savora.enums.TableStatus;
import vn.edu.fpt.savora.enums.VoucherStatus;
import vn.edu.fpt.savora.repository.DiningTableRepository;
import vn.edu.fpt.savora.repository.DishRepository;
import vn.edu.fpt.savora.repository.VoucherRepository;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Domain-Locked AI Chatbot Service for Savora Fine Dining & Restaurant Management System.
 * Strictly answers ONLY questions related to Savora Restaurant (live menu, table availability,
 * reservations, vouchers, QR ordering, hours, contact/feedback) and blocks unrelated questions.
 */
@Service
@RequiredArgsConstructor
public class SavoraAiChatbotService {

    private static final Pattern MATH_EXPR_PATTERN = Pattern.compile(".*\\b\\d+\\s*[+\\-*/^]\\s*\\d+\\b.*");

    private static final List<String> OFF_TOPIC_KEYWORDS = List.of(
            "python", "javascript", "java code", "c++", "html code", "write code", "programming", "leetcode",
            "algorithm", "react", "angular", "docker", "kubernetes", "bitcoin", "crypto", "ethereum", "stock market",
            "weather", "temperature in", "forecast", "president", "prime minister", "politics", "election",
            "football", "soccer", "world cup", "champions league", "messi", "ronaldo", "nba", "basketball",
            "capital of", "who invented", "world war", "planet", "solar system", "physics", "quantum", "chemistry",
            "solve equation", "integral", "derivative", "homework", "essay", "poem", "write a story", "joke"
    );

    private final DishRepository dishRepository;
    private final DiningTableRepository diningTableRepository;
    private final VoucherRepository voucherRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> processCustomerMessage(String rawMessage) {
        String msg = rawMessage != null ? rawMessage.trim() : "";
        String lower = msg.toLowerCase(Locale.ROOT);

        if (msg.isEmpty()) {
            return buildReply(
                    false,
                    "Hello! Please type a question about Savora Restaurant (menu dishes, table reservations, vouchers, opening hours, or feedback).",
                    List.of(
                            link("🪑 Book a Table", "/home#tables-section"),
                            link("🍽️ View Menu", "/home#menu"),
                            link("✉️ Contact & Feedback", "/contact")
                    )
            );
        }

        // 1. Strict Off-Topic Guardrail Check (Explicitly blocks unrelated topics)
        if (isExplicitlyOffTopic(lower)) {
            return buildBlockedReply();
        }

        List<Dish> dishes = dishRepository.findByStatusOrderByIsSignatureDescDishIdAsc(DishStatus.AVAILABLE);
        List<DiningTable> tables = diningTableRepository.findAllByOrderByTableCodeAsc();
        List<Voucher> vouchers = voucherRepository.findByStatusOrderByDiscountPercentDesc(VoucherStatus.ACTIVE);

        // 2. Check if user is asking about a specific dish in the database
        for (Dish d : dishes) {
            if (matchesDishQuery(lower, d)) {
                BigDecimal effPrice = d.getDiscountPrice() != null ? d.getDiscountPrice() : d.getPrice();
                String priceStr = fmtVnd(effPrice) + (d.getDiscountPrice() != null ? " (original " + fmtVnd(d.getPrice()) + ")" : "");
                String sig = Boolean.TRUE.equals(d.getIsSignature()) ? "★ Signature Dish • " : "";
                String reply = "🍽️ **" + d.getDishName() + "** (" + sig + (d.getCategory() != null ? d.getCategory().getCategoryName() : "Main Menu") + ")\n"
                        + "• **Price:** " + priceStr + "\n"
                        + "• **Prep Time:** ~" + (d.getPreparationTimeMinutes() != null ? d.getPreparationTimeMinutes() : 15) + " mins\n"
                        + "• **Details:** " + (d.getDescription() != null ? d.getDescription() : "Prepared fresh daily by our Executive Chef.");
                return buildReply(
                        false,
                        reply,
                        List.of(
                                link("🍽️ View on Signature Menu", "/home#menu"),
                                link("🪑 Reserve a Table to Dine", "/home#tables-section"),
                                link("🎁 Apply Voucher Deal", "/home#deals")
                        )
                );
            }
        }

        // 3. Vouchers / Promotions / Discounts
        if (containsAny(lower, "voucher", "deal", "discount", "promo", "offer", "coupon", "sale", "code")) {
            String voucherLines = vouchers.isEmpty()
                    ? "Currently no active promotional vouchers."
                    : vouchers.stream()
                    .map(v -> "• **" + v.getVoucherCode() + " (-" + v.getDiscountPercent() + "%)**: " + v.getDescription())
                    .collect(Collectors.joining("\n"));
            String reply = "🎁 **Active Savora Promotional Vouchers:**\n" + voucherLines
                    + "\n\nYou can select any of these voucher codes directly when booking a table online or when ordering via Table QR!";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🎁 Claim Promotional Deals", "/home#deals"),
                            link("🪑 Book Table with Deal", "/home#tables-section")
                    )
            );
        }

        // 4. Table Availability / Floor Map / VIP / Zones
        if (containsAny(lower, "table", "seat", "available", "floor", "vip", "terrace", "garden", "zone", "capacity", "room")) {
            List<DiningTable> availTables = tables.stream()
                    .filter(t -> t.getStatus() == TableStatus.AVAILABLE)
                    .collect(Collectors.toList());
            String availSummary = availTables.isEmpty()
                    ? "All tables are currently occupied or reserved for the current slot, but you can book later time slots today!"
                    : availTables.stream()
                    .map(t -> "**" + t.getTableCode() + "** (" + t.getArea() + ", max " + t.getCapacity() + " seats)")
                    .collect(Collectors.joining(", "));

            String reply = "🪑 **Live Dining Table Status (" + availTables.size() + "/" + tables.size() + " Currently Available):**\n"
                    + "• **Available Right Now:** " + availSummary + "\n"
                    + "• **Zones:** 1st Floor (Main Hall, 2–8 seats), 2nd Floor (Garden Terrace, 6–8 seats), and VIP Private Suites (12–16 seats).\n"
                    + "• **Turnaround Rule:** Each booking holds a table for **120 minutes dining + 10 minutes sanitizing (130 mins total)**.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🪑 View Live Floor Map", "/home#tables-section"),
                            link("📅 My Table Bookings", "/customer/reservations")
                    )
            );
        }

        // 5. How to Book / Reservation Policy / Cancel Booking
        if (containsAny(lower, "book", "reserve", "reservation", "slot", "cancel", "party size", "adult", "child")) {
            String reply = "📅 **How to Reserve a Table Online at Savora:**\n"
                    + "1. Go to our **Book a Table** form on the Home page and select **Adults**, **Children** (total guests must fit table seat capacity), **Arrival Date**, and **Time Slot (10:30 – 21:00)**.\n"
                    + "2. Pick an **Available Table** on the Floor Map and attach a **Promotional Voucher** (up to 20% OFF).\n"
                    + "3. Sign in and click **Reserve Table Now (Free)**. You can view or cancel your booking anytime in **My Bookings**.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🪑 Book a Table Now", "/home#tables-section"),
                            link("📅 My Bookings History", "/customer/reservations"),
                            link("🔐 Sign In", "/login")
                    )
            );
        }

        // 6. Menu / Dishes / Food / Drinks
        if (containsAny(lower, "menu", "dish", "food", "eat", "drink", "steak", "seafood", "salad", "dessert", "hotpot", "signature", "price", "culinary", "chef")) {
            String topDishes = dishes.stream().limit(5)
                    .map(d -> "• **" + d.getDishName() + "** — " + fmtVnd(d.getDiscountPrice() != null ? d.getDiscountPrice() : d.getPrice())
                            + (Boolean.TRUE.equals(d.getIsSignature()) ? " *(★ Signature)*" : ""))
                    .collect(Collectors.joining("\n"));
            String reply = "🍽️ **Savora Chef’s Signature Menu (" + dishes.size() + " Available Dishes):**\n"
                    + topDishes + "\n\n"
                    + "Our menu spans *Starters & Salads*, *Prime Steaks & Seafood*, *Hotpot & Grill*, *Artisan Desserts*, and *Wines & Cocktails*.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🍽️ Browse Full Menu", "/home#menu"),
                            link("🪑 Reserve a Table", "/home#tables-section")
                    )
            );
        }

        // 7. Table QR Ordering / Payment / VietQR
        if (containsAny(lower, "qr", "order", "kitchen", "pay", "payment", "bill", "invoice", "vietqr", "mbbank", "cash")) {
            String reply = "📱 **Table QR Ordering & Checkout Workflow:**\n"
                    + "• **At the Table:** Scan your table's unique QR code (`/qr/table/{tableCode}`) to order dishes directly to the Chef Kitchen Display System without needing to sign in.\n"
                    + "• **Live Kitchen Status:** Track every dish in real time from **PENDING → COOKING → READY → SERVED**.\n"
                    + "• **Billing & Payment:** When finished dining, our Waiter generates your official tax invoice with **VietQR (MBBank)** or **Cash** payment.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🍽️ View Signature Menu", "/home#menu"),
                            link("🪑 Book a Dining Table", "/home#tables-section")
                    )
            );
        }

        // 8. Contact / Feedback / Email / Location / Opening Hours
        if (containsAny(lower, "contact", "feedback", "email", "where", "location", "address", "hour", "open", "close", "phone", "hotline", "call")) {
            String reply = "📍 **Savora Fine Dining — Location, Hours & Guest Feedback:**\n"
                    + "• **Address:** Hoa Lac Hi-Tech Park, Thach That, Hanoi\n"
                    + "• **Opening Hours:** 10:00 AM – 10:30 PM Daily (Online booking slots: 10:30 – 21:00)\n"
                    + "• **Concierge Hotline:** 1900 6868 • **Email:** concierge@savora.vn\n"
                    + "• **Guest Feedback:** Visit our **Contact & Feedback** page to send us your thoughts and receive an instant confirmation email!";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("✉️ Open Contact & Feedback Form", "/contact"),
                            link("🪑 Book a Table", "/home#tables-section")
                    )
            );
        }

        // 9. Account / Profile / Sign In / Register
        if (containsAny(lower, "account", "profile", "login", "sign in", "register", "sign up", "password", "google")) {
            String reply = "👤 **Savora Member Account & Profile:**\n"
                    + "• You can sign in using your **Email & Password** or **Google** account to reserve tables online.\n"
                    + "• Once signed in, use **My Bookings** to manage reservations and **Profile** to update your name, phone number, gender, and address.";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("👤 My Account Profile", "/customer/profile"),
                            link("📅 My Bookings", "/customer/reservations"),
                            link("🔐 Sign In", "/login"),
                            link("📝 Sign Up", "/register")
                    )
            );
        }

        // 10. Greeting / General Project Overview
        if (containsAny(lower, "hi", "hello", "hey", "good morning", "good evening", "help", "savora", "project", "restaurant", "about", "what is", "who are you")) {
            String reply = "👋 Welcome to **Savora Fine Dining & Lounge AI Concierge**!\n"
                    + "I am strictly dedicated to assisting you with **Savora Restaurant**:\n"
                    + "• 🍽️ **Signature Menu & Dish Prices** (" + dishes.size() + " dishes available)\n"
                    + "• 🪑 **Real-Time Table Availability & Online Booking** (" + tables.size() + " tables across 3 zones)\n"
                    + "• 🎁 **Promotional Vouchers** (up to 20% OFF)\n"
                    + "• 📍 **Location, Opening Hours & Guest Feedback**\n\n"
                    + "Use the quick navigation links below or ask me anything about Savora!";
            return buildReply(
                    false,
                    reply,
                    List.of(
                            link("🪑 Book a Table", "/home#tables-section"),
                            link("🍽️ Signature Menu", "/home#menu"),
                            link("🎁 Voucher Deals", "/home#deals"),
                            link("✉️ Contact & Feedback", "/contact")
                    )
            );
        }

        // 11. Default Strict Off-Topic Block for any other unrelated input
        return buildBlockedReply();
    }

    private boolean isExplicitlyOffTopic(String lower) {
        if (MATH_EXPR_PATTERN.matcher(lower).matches()) {
            return true;
        }
        for (String kw : OFF_TOPIC_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDishQuery(String lower, Dish d) {
        if (d.getDishName() == null) return false;
        String nameLower = d.getDishName().toLowerCase(Locale.ROOT);
        if (lower.contains(nameLower)) return true;
        if (nameLower.contains("salmon") && containsAny(lower, "salmon")) return true;
        if (nameLower.contains("tenderloin") && containsAny(lower, "tenderloin", "steak", "beef")) return true;
        if (nameLower.contains("lobster") && containsAny(lower, "lobster")) return true;
        if (nameLower.contains("hotpot") && containsAny(lower, "hotpot", "hot pot")) return true;
        if (nameLower.contains("pumpkin") && containsAny(lower, "pumpkin", "truffle soup")) return true;
        if (nameLower.contains("chocolate") && containsAny(lower, "chocolate", "lava cake", "gelato")) return true;
        if (nameLower.contains("peach") && containsAny(lower, "peach", "lemongrass")) return true;
        if (nameLower.contains("ribs") && containsAny(lower, "ribs", "pork", "iberico")) return true;
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private Map<String, Object> buildBlockedReply() {
        String msg = "🚫 **Off-Topic Question Blocked**\n"
                + "I am the **Savora Fine Dining AI Assistant**, and I am strictly configured to **only answer questions about the Savora Restaurant project** (our menu dishes, table availability, online reservations, promotional vouchers, QR ordering, opening hours, and guest feedback).\n\n"
                + "I cannot answer unrelated questions. Please explore Savora using the links below:";
        return buildReply(
                true,
                msg,
                List.of(
                        link("🪑 Book a Table", "/home#tables-section"),
                        link("🍽️ View Signature Menu", "/home#menu"),
                        link("🎁 Promotional Deals", "/home#deals"),
                        link("✉️ Contact & Feedback", "/contact")
                )
        );
    }

    private Map<String, Object> buildReply(boolean blocked, String answer, List<Map<String, String>> links) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("blocked", blocked);
        res.put("answer", answer);
        res.put("links", links);
        return res;
    }

    private Map<String, String> link(String label, String url) {
        return Map.of("label", label, "url", url);
    }

    private String fmtVnd(BigDecimal amount) {
        if (amount == null) return "0 VND";
        return NumberFormat.getNumberInstance(Locale.US).format(amount.longValue()) + " VND";
    }
}
