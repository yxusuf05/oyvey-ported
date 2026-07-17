/* ============================================================
   Flexshake — shared interactions
   - Mobile navigation
   - Product page: gallery, colour variants, quantity, checkout
   - Login page: tab switch + redirect to Shopify customer account
   - Contact page: mailto form
   ============================================================ */

const SHOP_URL = "https://flexshake.myshopify.com";
const CONTACT_EMAIL = "vyra.perfums@gmail.com";

/* Shopify variant IDs for the Portable Blender (product 10995577979223) */
const VARIANTS = {
  blue:   { id: "54049178845527", nameKey: "color.blue" },
  green:  { id: "54049178878295", nameKey: "color.green" },
  pink:   { id: "54049178911063", nameKey: "color.pink" },
  purple: { id: "54049178943831", nameKey: "color.purple" },
};

document.addEventListener("DOMContentLoaded", () => {
  initMobileNav();
  initProductPage();
  initLoginPage();
  initContactForm();
});

/* ---------- Mobile navigation ---------- */
function initMobileNav() {
  const toggle = document.querySelector(".nav-toggle");
  const nav = document.querySelector(".main-nav");
  if (!toggle || !nav) return;
  toggle.addEventListener("click", () => {
    const open = nav.classList.toggle("open");
    toggle.setAttribute("aria-expanded", String(open));
  });
}

/* ---------- Product page ---------- */
function initProductPage() {
  const buyBtn = document.getElementById("buy-button");
  if (!buyBtn) return; // not on the product page

  let selectedColor = "blue";

  // Gallery
  const mainImg = document.getElementById("gallery-main-img");
  document.querySelectorAll(".gallery-thumbs button").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".gallery-thumbs button").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const img = btn.querySelector("img");
      if (mainImg && img) {
        mainImg.src = img.src;
        mainImg.alt = img.alt;
      }
    });
  });

  // Colour swatches
  const colorNameEl = document.getElementById("selected-color-name");
  function updateColorName() {
    if (colorNameEl) colorNameEl.textContent = t(VARIANTS[selectedColor].nameKey);
  }
  document.querySelectorAll(".swatch").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".swatch").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      selectedColor = btn.dataset.color;
      updateColorName();
    });
  });
  document.addEventListener("langchange", updateColorName);
  updateColorName();

  // Quantity stepper
  const qtyInput = document.getElementById("qty-input");
  const clampQty = (v) => Math.min(10, Math.max(1, isNaN(v) ? 1 : v));
  document.getElementById("qty-minus").addEventListener("click", () => {
    qtyInput.value = clampQty(parseInt(qtyInput.value, 10) - 1);
  });
  document.getElementById("qty-plus").addEventListener("click", () => {
    qtyInput.value = clampQty(parseInt(qtyInput.value, 10) + 1);
  });
  qtyInput.addEventListener("change", () => {
    qtyInput.value = clampQty(parseInt(qtyInput.value, 10));
  });

  // Buy → Shopify cart permalink → checkout
  buyBtn.addEventListener("click", () => {
    const qty = clampQty(parseInt(qtyInput.value, 10));
    const variantId = VARIANTS[selectedColor].id;
    window.open(`${SHOP_URL}/cart/${variantId}:${qty}`, "_blank", "noopener");
  });
}

/* ---------- Login page ---------- */
function initLoginPage() {
  const authCard = document.getElementById("auth-card");
  if (!authCard) return;

  const tabs = document.querySelectorAll(".auth-tabs button");
  const signinForm = document.getElementById("signin-form");
  const registerForm = document.getElementById("register-form");

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      tabs.forEach((tb) => tb.classList.remove("active"));
      tab.classList.add("active");
      const isSignin = tab.dataset.tab === "signin";
      signinForm.style.display = isSignin ? "" : "none";
      registerForm.style.display = isSignin ? "none" : "";
    });
  });

  // Both forms redirect to the real Shopify customer account pages.
  signinForm.addEventListener("submit", (e) => {
    e.preventDefault();
    window.location.href = `${SHOP_URL}/account/login`;
  });
  registerForm.addEventListener("submit", (e) => {
    e.preventDefault();
    window.location.href = `${SHOP_URL}/account/register`;
  });
}

/* ---------- Contact form (mailto fallback) ---------- */
function initContactForm() {
  const form = document.getElementById("contact-form");
  if (!form) return;
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const name = document.getElementById("contact-name").value.trim();
    const email = document.getElementById("contact-email").value.trim();
    const message = document.getElementById("contact-message").value.trim();
    const subject = encodeURIComponent(`Flexshake – Anfrage von ${name || "Website"}`);
    const body = encodeURIComponent(`${message}\n\n—\n${name}\n${email}`);
    window.location.href = `mailto:${CONTACT_EMAIL}?subject=${subject}&body=${body}`;
  });
}
