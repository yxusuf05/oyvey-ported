/* ============================================================
   Flexshake — i18n (DE default, EN secondary)
   Usage: <element data-i18n="key">German fallback text</element>
          <input data-i18n-placeholder="key">
   ============================================================ */

const I18N = {
  de: {
    // Navigation / header
    "nav.home": "Start",
    "nav.product": "Produkt",
    "nav.faq": "FAQ",
    "nav.contact": "Kontakt",
    "nav.login": "Login",
    "nav.buy": "Jetzt kaufen",

    // Landing — hero
    "hero.eyebrow": "Frisch gemixt, wo du bist",
    "hero.title": "Dein Smoothie.<br><em>Überall.</em>",
    "hero.lead": "Der Flexshake Portable Blender mixt Obst, Gemüse und Eis in Sekunden zu cremigen Smoothies – kabellos, USB-aufladbar und so kompakt, dass er in jede Tasche passt.",
    "hero.cta.buy": "Jetzt für 49,00 € kaufen",
    "hero.cta.more": "Mehr erfahren",
    "hero.badge.usb": "USB-aufladbar",
    "hero.badge.capacity": "380 ml Kapazität",
    "hero.badge.clean": "Selbstreinigend",
    "hero.price": "49,00 €",
    "hero.price.note": "inkl. MwSt.",

    // Landing — features
    "features.title": "Warum Flexshake?",
    "features.sub": "Ein Mixer, der sich deinem Alltag anpasst – nicht umgekehrt.",
    "features.f1.title": "6-Klingen-Power",
    "features.f1.text": "Sechs Edelstahlklingen zerkleinern Obst, Gemüse und sogar Eis mühelos – für perfekt cremige Smoothies ohne Stückchen.",
    "features.f2.title": "Selbstreinigung",
    "features.f2.text": "Einfach Wasser einfüllen, Knopf drücken, fertig. Der Flexshake reinigt sich in Sekunden selbst – kein Auseinanderbauen nötig.",
    "features.f3.title": "USB-aufladbar",
    "features.f3.text": "Laden per Powerbank, Laptop oder Netzteil. Eine volle Ladung reicht für bis zu 5 Smoothies – ideal für unterwegs.",
    "features.f4.title": "Lebensmittelecht",
    "features.f4.text": "Gehäuse und Becher aus BPA-freiem, lebensmittelechtem PP/ABS-Material – sicher, geschmacksneutral und langlebig.",

    // Landing — steps
    "steps.title": "So einfach geht's",
    "steps.sub": "Vom Obst zum Smoothie in unter einer Minute.",
    "steps.s1.title": "Befüllen",
    "steps.s1.text": "Lieblingsobst, etwas Flüssigkeit und nach Wunsch Eis in den 380-ml-Becher geben.",
    "steps.s2.title": "Mixen",
    "steps.s2.text": "Knopf drücken – die 6 Klingen mixen alles in ca. 30 Sekunden cremig.",
    "steps.s3.title": "Genießen",
    "steps.s3.text": "Deckel drauf und direkt aus dem Becher trinken – zuhause, im Büro oder im Gym.",

    // Landing — gallery
    "gallery.title": "Für jeden Moment gemacht",
    "gallery.sub": "Morgens am Frühstückstisch, mittags im Büro, nach dem Training – Flexshake ist dabei.",

    // Landing — testimonials
    "reviews.title": "Das sagen unsere Kund:innen",
    "reviews.sub": "Über 1.000 Smoothies werden jede Woche mit Flexshake gemixt.",
    "reviews.r1.text": "„Ich nehme ihn jeden Tag mit ins Büro. Aufladen per USB am Laptop ist genial – und die Selbstreinigung spart mir so viel Zeit.“",
    "reviews.r1.name": "Lena M.",
    "reviews.r2.text": "„Endlich ein Mini-Mixer, der auch gefrorene Beeren schafft. Die 6 Klingen machen wirklich einen Unterschied.“",
    "reviews.r2.name": "Jonas K.",
    "reviews.r3.text": "„Perfekt fürs Gym: Proteinshake in 30 Sekunden, direkt aus dem Becher trinken, fertig. Klare Empfehlung!“",
    "reviews.r3.name": "Sarah T.",
    "reviews.note": "Beispiel-Bewertungen zur Veranschaulichung.",

    // Landing — CTA banner
    "cta.title": "Bereit für deinen ersten Shake?",
    "cta.sub": "Jetzt für <span class=\"price\">49,00 €</span> sichern – in 4 Farben erhältlich.",
    "cta.button": "Zum Produkt",

    // Product page
    "product.title": "Portable Blender – USB-Mixer für Smoothies & Shakes",
    "product.price": "49,00 €",
    "product.price.note": "inkl. MwSt., zzgl. Versand",
    "product.shortdesc": "Der kompakte 380-ml-Mixer mit 6 Edelstahlklingen und USB-Akku. Mixt Smoothies, Shakes und Säfte in ca. 30 Sekunden – überall.",
    "product.option.color": "Farbe:",
    "product.qty": "Menge",
    "product.buy": "Jetzt kaufen – zur Kasse",
    "product.secure": "Sicherer Checkout über Shopify · Käuferschutz inklusive",
    "product.d1.title": "Beschreibung",
    "product.d1.text": "Sechs Klingen statt vier: Der Flexshake Portable Blender ist kraftvoller als klassische Mini-Mixer und verwandelt Obst, Gemüse und Eis mühelos in cremige Smoothies. Dank Selbstreinigungsfunktion einfach Wasser einfüllen und Knopf drücken – kein Auseinanderbauen nötig. Der integrierte Akku wird bequem per USB geladen (Powerbank, Laptop oder Netzteil) und reicht für ca. 5 Anwendungen pro Ladung.",
    "product.d2.title": "Technische Daten",
    "product.spec.power": "Leistung",
    "product.spec.power.v": "15 W",
    "product.spec.capacity": "Kapazität",
    "product.spec.capacity.v": "380 ml",
    "product.spec.battery": "Akku",
    "product.spec.battery.v": "USB-aufladbar, ca. 5 Anwendungen pro Ladung",
    "product.spec.blades": "Klingen",
    "product.spec.blades.v": "6 Edelstahlklingen",
    "product.spec.material": "Material",
    "product.spec.material.v": "Lebensmittelechtes PP/ABS, Becher aus PC (BPA-frei)",
    "product.spec.size": "Maße",
    "product.spec.size.v": "7,5 × 7,5 × 23 cm",
    "product.spec.voltage": "Spannung",
    "product.spec.voltage.v": "≤ 36 V",
    "product.d3.title": "Lieferumfang",
    "product.d3.i1": "1 × Flexshake Portable Blender",
    "product.d3.i2": "1 × USB-Ladekabel",
    "product.d3.i3": "1 × Geschenkbox",
    "product.d4.title": "Versand & Rückgabe",
    "product.d4.text": "Versand innerhalb Deutschlands in der Regel in 2–5 Werktagen. 14 Tage Widerrufsrecht ab Erhalt der Ware.",

    // Colors
    "color.blue": "Blau",
    "color.green": "Grün",
    "color.pink": "Pink",
    "color.purple": "Lila",

    // Login
    "login.title": "Willkommen zurück",
    "login.sub": "Melde dich bei deinem Flexshake-Kundenkonto an, um Bestellungen und Adressen zu verwalten.",
    "login.tab.signin": "Anmelden",
    "login.tab.register": "Registrieren",
    "login.email": "E-Mail-Adresse",
    "login.password": "Passwort",
    "login.firstname": "Vorname",
    "login.lastname": "Nachname",
    "login.submit.signin": "Anmelden",
    "login.submit.register": "Konto erstellen",
    "login.note": "Du wirst sicher zu deinem Flexshake-Kundenkonto (Shopify) weitergeleitet.",

    // FAQ
    "faq.title": "Häufige Fragen",
    "faq.sub": "Alles, was du über deinen Flexshake wissen musst.",
    "faq.q1": "Wie lange hält eine Akkuladung?",
    "faq.a1": "Eine volle Ladung reicht für ca. 5 Mixvorgänge. Geladen wird bequem per USB – an der Powerbank, am Laptop oder mit einem Netzteil.",
    "faq.q2": "Kann der Mixer Eis und gefrorenes Obst zerkleinern?",
    "faq.a2": "Ja. Dank der 6 Edelstahlklingen schafft der Flexshake auch Eiswürfel und gefrorene Beeren. Tipp: Immer etwas Flüssigkeit hinzugeben, dann mixt es sich am besten.",
    "faq.q3": "Wie reinige ich den Mixer?",
    "faq.a3": "Ganz einfach: Etwas Wasser (und bei Bedarf einen Tropfen Spülmittel) einfüllen, Knopf drücken – der Mixer reinigt sich selbst. Danach kurz ausspülen, fertig.",
    "faq.q4": "Wie groß ist der Becher?",
    "faq.a4": "Der Becher fasst 380 ml – ideal für einen Smoothie, Shake oder Saft für eine Person.",
    "faq.q5": "Welche Farben gibt es?",
    "faq.a5": "Den Flexshake gibt es in vier Farben: Blau, Grün, Pink und Lila.",
    "faq.q6": "Wie lange dauert der Versand?",
    "faq.a6": "Bestellungen werden in der Regel innerhalb von 2–5 Werktagen innerhalb Deutschlands geliefert. Du erhältst eine Versandbestätigung mit Tracking per E-Mail.",
    "faq.q7": "Kann ich meine Bestellung zurückgeben?",
    "faq.a7": "Ja, du hast ein 14-tägiges Widerrufsrecht ab Erhalt der Ware. Kontaktiere uns einfach über die Kontaktseite, wir helfen dir weiter.",
    "faq.q8": "Ist das Material lebensmittelecht?",
    "faq.a8": "Ja. Becher und Gehäuse bestehen aus lebensmittelechtem, BPA-freiem PP/ABS- bzw. PC-Material – geschmacksneutral und sicher.",

    // Contact
    "contact.title": "Kontakt",
    "contact.sub": "Fragen zu deiner Bestellung oder zum Produkt? Wir helfen gern.",
    "contact.card.title": "So erreichst du uns",
    "contact.card.text": "Schreib uns eine E-Mail – wir antworten in der Regel innerhalb von 24 Stunden (Mo–Fr).",
    "contact.form.name": "Dein Name",
    "contact.form.email": "Deine E-Mail-Adresse",
    "contact.form.message": "Deine Nachricht",
    "contact.form.submit": "Nachricht senden",
    "contact.form.note": "Der Button öffnet dein E-Mail-Programm mit der vorbereiteten Nachricht.",

    // Cookie-Banner
    "cookie.text": "🍪 <strong>Cookies:</strong> Wir verwenden nur technisch notwendige Cookies für Warenkorb &amp; Checkout – kein Tracking. Mehr dazu in der <a href=\"datenschutz.html\">Datenschutzerklärung</a>.",
    "cookie.accept": "Akzeptieren",
    "cookie.decline": "Nur notwendige",

    // Footer
    "footer.tagline": "Frische Smoothies, wo immer du bist. Kompakt, kabellos, selbstreinigend.",
    "footer.shop": "Shop",
    "footer.help": "Hilfe",
    "footer.legal": "Rechtliches",
    "footer.imprint": "Impressum",
    "footer.privacy": "Datenschutz",
    "footer.copyright": "© 2026 Flexshake. Alle Rechte vorbehalten.",
    "footer.checkout": "Checkout & Zahlung sicher über Shopify",
  },

  en: {
    // Navigation / header
    "nav.home": "Home",
    "nav.product": "Product",
    "nav.faq": "FAQ",
    "nav.contact": "Contact",
    "nav.login": "Login",
    "nav.buy": "Buy now",

    // Landing — hero
    "hero.eyebrow": "Freshly blended, wherever you are",
    "hero.title": "Your smoothie.<br><em>Anywhere.</em>",
    "hero.lead": "The Flexshake Portable Blender turns fruit, veggies and ice into creamy smoothies in seconds – cordless, USB-rechargeable and compact enough to fit in any bag.",
    "hero.cta.buy": "Buy now for €49.00",
    "hero.cta.more": "Learn more",
    "hero.badge.usb": "USB rechargeable",
    "hero.badge.capacity": "380 ml capacity",
    "hero.badge.clean": "Self-cleaning",
    "hero.price": "€49.00",
    "hero.price.note": "incl. VAT",

    // Landing — features
    "features.title": "Why Flexshake?",
    "features.sub": "A blender that adapts to your day – not the other way around.",
    "features.f1.title": "6-blade power",
    "features.f1.text": "Six stainless-steel blades crush fruit, veggies and even ice with ease – for perfectly creamy smoothies without chunks.",
    "features.f2.title": "Self-cleaning",
    "features.f2.text": "Just add water, press the button, done. Flexshake cleans itself in seconds – no disassembly required.",
    "features.f3.title": "USB rechargeable",
    "features.f3.text": "Charge via power bank, laptop or wall adapter. One full charge blends up to 5 smoothies – perfect on the go.",
    "features.f4.title": "Food-grade material",
    "features.f4.text": "Body and cup made from BPA-free, food-grade PP/ABS – safe, taste-neutral and built to last.",

    // Landing — steps
    "steps.title": "As easy as it gets",
    "steps.sub": "From fruit to smoothie in under a minute.",
    "steps.s1.title": "Fill",
    "steps.s1.text": "Add your favourite fruit, some liquid and ice if you like to the 380 ml cup.",
    "steps.s2.title": "Blend",
    "steps.s2.text": "Press the button – the 6 blades blend everything creamy in about 30 seconds.",
    "steps.s3.title": "Enjoy",
    "steps.s3.text": "Pop the lid on and drink straight from the cup – at home, at work or at the gym.",

    // Landing — gallery
    "gallery.title": "Made for every moment",
    "gallery.sub": "At the breakfast table, at the office, after your workout – Flexshake comes along.",

    // Landing — testimonials
    "reviews.title": "What our customers say",
    "reviews.sub": "Over 1,000 smoothies are blended with Flexshake every week.",
    "reviews.r1.text": "“I take it to the office every day. Charging via USB from my laptop is genius – and the self-cleaning saves me so much time.”",
    "reviews.r1.name": "Lena M.",
    "reviews.r2.text": "“Finally a mini blender that handles frozen berries. The 6 blades really make a difference.”",
    "reviews.r2.name": "Jonas K.",
    "reviews.r3.text": "“Perfect for the gym: protein shake in 30 seconds, drink straight from the cup, done. Highly recommended!”",
    "reviews.r3.name": "Sarah T.",
    "reviews.note": "Sample reviews for illustration purposes.",

    // Landing — CTA banner
    "cta.title": "Ready for your first shake?",
    "cta.sub": "Get yours now for <span class=\"price\">€49.00</span> – available in 4 colours.",
    "cta.button": "View product",

    // Product page
    "product.title": "Portable Blender – USB mixer for smoothies & shakes",
    "product.price": "€49.00",
    "product.price.note": "incl. VAT, plus shipping",
    "product.shortdesc": "The compact 380 ml blender with 6 stainless-steel blades and a USB-rechargeable battery. Blends smoothies, shakes and juices in about 30 seconds – anywhere.",
    "product.option.color": "Colour:",
    "product.qty": "Quantity",
    "product.buy": "Buy now – checkout",
    "product.secure": "Secure checkout via Shopify · buyer protection included",
    "product.d1.title": "Description",
    "product.d1.text": "Six blades instead of four: the Flexshake Portable Blender is more powerful than classic mini blenders and effortlessly turns fruit, veggies and ice into creamy smoothies. Thanks to the self-cleaning function, just add water and press the button – no disassembly needed. The built-in battery charges conveniently via USB (power bank, laptop or wall adapter) and lasts for about 5 uses per charge.",
    "product.d2.title": "Specifications",
    "product.spec.power": "Power",
    "product.spec.power.v": "15 W",
    "product.spec.capacity": "Capacity",
    "product.spec.capacity.v": "380 ml",
    "product.spec.battery": "Battery",
    "product.spec.battery.v": "USB rechargeable, approx. 5 uses per charge",
    "product.spec.blades": "Blades",
    "product.spec.blades.v": "6 stainless-steel blades",
    "product.spec.material": "Material",
    "product.spec.material.v": "Food-grade PP/ABS, PC cup (BPA-free)",
    "product.spec.size": "Dimensions",
    "product.spec.size.v": "7.5 × 7.5 × 23 cm",
    "product.spec.voltage": "Voltage",
    "product.spec.voltage.v": "≤ 36 V",
    "product.d3.title": "What's in the box",
    "product.d3.i1": "1 × Flexshake Portable Blender",
    "product.d3.i2": "1 × USB charging cable",
    "product.d3.i3": "1 × gift box",
    "product.d4.title": "Shipping & returns",
    "product.d4.text": "Shipping within Germany usually takes 2–5 business days. 14-day right of withdrawal from receipt of the goods.",

    // Colors
    "color.blue": "Blue",
    "color.green": "Green",
    "color.pink": "Pink",
    "color.purple": "Purple",

    // Login
    "login.title": "Welcome back",
    "login.sub": "Sign in to your Flexshake customer account to manage orders and addresses.",
    "login.tab.signin": "Sign in",
    "login.tab.register": "Register",
    "login.email": "Email address",
    "login.password": "Password",
    "login.firstname": "First name",
    "login.lastname": "Last name",
    "login.submit.signin": "Sign in",
    "login.submit.register": "Create account",
    "login.note": "You will be securely redirected to your Flexshake customer account (Shopify).",

    // FAQ
    "faq.title": "Frequently asked questions",
    "faq.sub": "Everything you need to know about your Flexshake.",
    "faq.q1": "How long does one battery charge last?",
    "faq.a1": "A full charge lasts for about 5 blending cycles. Charging is easy via USB – from a power bank, laptop or wall adapter.",
    "faq.q2": "Can it crush ice and frozen fruit?",
    "faq.a2": "Yes. Thanks to the 6 stainless-steel blades, Flexshake handles ice cubes and frozen berries. Tip: always add some liquid for the best blending results.",
    "faq.q3": "How do I clean the blender?",
    "faq.a3": "Easy: add some water (and a drop of dish soap if needed), press the button – the blender cleans itself. Rinse briefly afterwards, done.",
    "faq.q4": "How big is the cup?",
    "faq.a4": "The cup holds 380 ml – ideal for one smoothie, shake or juice per person.",
    "faq.q5": "Which colours are available?",
    "faq.a5": "Flexshake comes in four colours: blue, green, pink and purple.",
    "faq.q6": "How long does shipping take?",
    "faq.a6": "Orders are usually delivered within 2–5 business days within Germany. You'll receive a shipping confirmation with tracking by email.",
    "faq.q7": "Can I return my order?",
    "faq.a7": "Yes, you have a 14-day right of withdrawal from receipt of the goods. Just reach out via the contact page and we'll help you.",
    "faq.q8": "Is the material food-safe?",
    "faq.a8": "Yes. The cup and body are made from food-grade, BPA-free PP/ABS and PC materials – taste-neutral and safe.",

    // Contact
    "contact.title": "Contact",
    "contact.sub": "Questions about your order or the product? We're happy to help.",
    "contact.card.title": "How to reach us",
    "contact.card.text": "Send us an email – we usually reply within 24 hours (Mon–Fri).",
    "contact.form.name": "Your name",
    "contact.form.email": "Your email address",
    "contact.form.message": "Your message",
    "contact.form.submit": "Send message",
    "contact.form.note": "The button opens your email app with the prepared message.",

    // Cookie banner
    "cookie.text": "🍪 <strong>Cookies:</strong> We only use technically necessary cookies for the cart &amp; checkout – no tracking. Learn more in our <a href=\"datenschutz.html\">privacy policy</a>.",
    "cookie.accept": "Accept",
    "cookie.decline": "Essential only",

    // Footer
    "footer.tagline": "Fresh smoothies wherever you are. Compact, cordless, self-cleaning.",
    "footer.shop": "Shop",
    "footer.help": "Help",
    "footer.legal": "Legal",
    "footer.imprint": "Imprint",
    "footer.privacy": "Privacy policy",
    "footer.copyright": "© 2026 Flexshake. All rights reserved.",
    "footer.checkout": "Checkout & payment secured by Shopify",
  },
};

const LANG_KEY = "flexshake-lang";

function getLang() {
  const stored = localStorage.getItem(LANG_KEY);
  return stored === "en" ? "en" : "de";
}

function applyLang(lang) {
  const dict = I18N[lang] || I18N.de;
  document.documentElement.lang = lang;

  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const key = el.getAttribute("data-i18n");
    if (dict[key] !== undefined) el.innerHTML = dict[key];
  });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
    const key = el.getAttribute("data-i18n-placeholder");
    if (dict[key] !== undefined) el.setAttribute("placeholder", dict[key]);
  });

  document.querySelectorAll(".lang-switch button").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.lang === lang);
  });

  document.dispatchEvent(new CustomEvent("langchange", { detail: { lang } }));
}

function setLang(lang) {
  localStorage.setItem(LANG_KEY, lang);
  applyLang(lang);
}

function t(key) {
  const dict = I18N[getLang()] || I18N.de;
  return dict[key] !== undefined ? dict[key] : key;
}

document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".lang-switch button").forEach((btn) => {
    btn.addEventListener("click", () => setLang(btn.dataset.lang));
  });
  applyLang(getLang());
});
