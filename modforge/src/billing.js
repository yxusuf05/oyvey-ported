// Stripe integration, fully env-gated: without keys the checkout endpoint
// returns a stub (and ALLOW_DEV_UPGRADE=1 exposes a local upgrade switch for
// testing); with keys it creates real subscription checkouts and processes
// signature-verified webhooks. No live calls are needed to run the app.
import Stripe from 'stripe';
import express from 'express';
import { config } from '../config.js';
import { db } from '../db.js';
import { requireAuth } from './auth.js';

const stripe = config.stripe.secretKey ? new Stripe(config.stripe.secretKey) : null;

export const billingRouter = express.Router();

billingRouter.post('/checkout', requireAuth, async (req, res) => {
  if (!stripe || !config.stripe.priceIdPremium) {
    return res.json({
      stub: true,
      message: 'Zahlungen sind noch nicht konfiguriert (STRIPE_SECRET_KEY / STRIPE_PRICE_ID_PREMIUM fehlen). '
        + (config.allowDevUpgrade ? 'Dev-Modus: POST /api/billing/dev-upgrade schaltet dieses Konto auf Premium.' : ''),
    });
  }
  try {
    let customerId = req.user.stripe_customer_id;
    if (!customerId) {
      const customer = await stripe.customers.create({ email: req.user.email, metadata: { user_id: String(req.user.id) } });
      customerId = customer.id;
      db.prepare('UPDATE users SET stripe_customer_id=? WHERE id=?').run(customerId, req.user.id);
    }
    const session = await stripe.checkout.sessions.create({
      mode: 'subscription',
      customer: customerId,
      line_items: [{ price: config.stripe.priceIdPremium, quantity: 1 }],
      success_url: `${req.protocol}://${req.get('host')}/?upgraded=1`,
      cancel_url: `${req.protocol}://${req.get('host')}/`,
    });
    res.json({ url: session.url });
  } catch (e) {
    res.status(502).json({ error: 'Stripe-Fehler: ' + e.message });
  }
});

billingRouter.post('/dev-upgrade', requireAuth, (req, res) => {
  if (!config.allowDevUpgrade) return res.status(403).json({ error: 'Dev-Upgrade ist deaktiviert (ALLOW_DEV_UPGRADE).' });
  const plan = req.user.plan === 'premium' ? 'free' : 'premium';
  db.prepare('UPDATE users SET plan=? WHERE id=?').run(plan, req.user.id);
  res.json({ plan });
});

// Mounted with express.raw BEFORE the json body parser (signature needs the raw body).
export function webhookHandler(req, res) {
  if (!stripe || !config.stripe.webhookSecret) return res.status(501).json({ error: 'webhook not configured' });
  let event;
  try {
    event = stripe.webhooks.constructEvent(req.body, req.headers['stripe-signature'], config.stripe.webhookSecret);
  } catch (e) {
    return res.status(400).json({ error: 'invalid signature' });
  }
  const setPlanByCustomer = (customerId, plan) =>
    db.prepare('UPDATE users SET plan=? WHERE stripe_customer_id=?').run(plan, customerId);

  switch (event.type) {
    case 'checkout.session.completed':
      setPlanByCustomer(event.data.object.customer, 'premium');
      break;
    case 'customer.subscription.deleted':
      setPlanByCustomer(event.data.object.customer, 'free');
      break;
  }
  res.json({ received: true });
}
