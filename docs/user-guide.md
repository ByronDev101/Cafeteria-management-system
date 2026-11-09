# User guide

The Smart Cafeteria system lets students order from their table by scanning a QR code, lets staff handle the orders,
lets managers look after the menu, reports and student credit, and lets administrators look after accounts and tables.

**This is a prototype.** Payments and credit are records written down by staff. No real money moves through the system,
and the screens say so.

Signing in works the same for everyone: open the address you were given, enter your account name and password, and you
land on the page for your role. If you were given a temporary password, you must choose your own before doing anything
else. After 30 minutes without activity you are signed out. Use **Change password** in the menu whenever you like.

---

## 1. Students

### Placing an order
1. Scan the QR code on your table with your phone camera and open the link. Sign in if asked. The page remembers your
   table. (If a QR code is damaged or does not work, tell staff; do not guess at addresses.)
2. Open **View menu**. Items that are sold out are marked and cannot be added.
3. Add items to **Your cart**. Change quantities or remove items there. Prices always come from the menu, never from
   your browser.
4. Choose how you will pay:
   - **Cash**: staff write down your payment when they receive it.
   - **M-Pesa till**: pay at the till as usual. Staff write down your payment and may note your M-Pesa code.
   - **Cafeteria credit**: shown only if a manager has given you a credit limit, with the amount available now.
5. Press the button to place the order. You get an order number such as `SC-ABC123`. Pressing twice does not place
   two orders.

If an item sold out while you were choosing, the cart tells you which one so you can change it.

### Following your order
- **My orders** lists your orders. Open one to see its status (Submitted, Accepted, Preparing, Ready, Completed or
  Cancelled), the history of changes, and a **payment status** with a short note.
- The page refreshes itself when the status changes.
- You can see only your own orders.

### Cancelling
You can cancel while the order is still **Submitted**. Once staff accept it, you cannot change it: ask staff. A
cancelled credit order releases the credit that was held.

### Your credit
**My credit** shows your limit, what is held for orders waiting for staff, what you owe, what is available, and your
recent activity. You cannot change any of it. You pay what you owe at the cafeteria; a manager records the payment.

---

## 2. Cafeteria staff

### The order board (**Order board**)
New orders appear by themselves, usually within a few seconds. Each ticket shows the order number, time, table, the
student's name, the items, the total, the payment method and the status.

Use the tabs to filter: **All active**, **New**, **In the kitchen**, **Ready to serve**, **Finished today**, and
**Refunds due** (with a count).

### Moving an order along
Each ticket has one button for the next step: **Accept order**, then Preparing, Ready, and Completed. Steps happen in
that order only. If two people press at the same moment, the second sees a message and the board refreshes.

### Cancelling
Use **Cancel order** and give a reason (for example "out of stock"). The reason is required and is kept in the order's
history. You can cancel until the order is Completed.

### Payments
- Each ticket shows a payment status: **Awaiting payment**, **Payment received**, **No payment taken**, **Refund due**
  or **Refunded**.
- When you receive cash or an M-Pesa payment, press **Mark payment received**. For M-Pesa you may type the code
  (optional, up to 40 characters).
- If an order is cancelled after its payment was received, it moves to **Refunds due**. After you hand the money back,
  press **Mark refund paid**. Refunds due stay on that tab until paid, even on later days.
- Credit orders are charged automatically when you accept them. There is no payment button for them.
- All of this is a record of what you did. Nothing is charged or refunded electronically.

### Item availability (**Item availability**)
Mark an item **unavailable** when it runs out, and **available** again when it returns. Students see the change at
once.

---

## 3. Managers

### Menu (**Manage menu**)
Create, edit, deactivate and reactivate categories and items. For each item set the name, description, price (in KSh,
with up to two decimals) and availability. You can add or remove a photo. Price changes are written to the audit log.

### Reports (**Reports**)
Pick a start and end date (campus time). With nothing chosen you see the last 7 days. A range that makes no sense, or
longer than 366 days, falls back to the last 7 days with a warning.

- **Payments:** records by status with amounts.
- **Student credit:** what students owe now, what is held, and credit activity in the chosen days.
- **Orders by status** and **payments received by method**.
- **Orders:** search by status, order number and table. The newest 100 are shown, with a note if there are more.
  Student names are not shown here.

### Student credit (**Student credit**)
1. Search for a student by name or username (the page first lists students who already have credit).
2. Type the **credit limit** in KSh (0 to 50,000) and press **Set limit**. The student can then use credit up to that
   amount, counting what is held and what they owe.
3. When a student pays what they owe, enter the amount (and an optional note) and press **Record payment**. It cannot
   be more than they owe.
Lowering a limit below what a student has already used blocks new credit orders but does not undo existing ones.

---

## 4. Administrators

### Accounts (**Accounts**)
- **New account:** enter the details and choose a role. The system shows a temporary password once. Give it to the
  person privately; they must change it at first sign-in. Do not write it in chat or email.
- Open an account to change its **roles**, **deactivate** or **reactivate** it, **reset its password**, or **unlock** it
  (an account locks after five wrong passwords).
- Every role and status change is written to the audit log.

### Tables and QR codes (**Tables and QR codes**)
- Add a table, rename it, or deactivate it. Each table has a QR code made from a random token. The token is stored
  only in scrambled form and is **shown once**, so print or download the QR image right away.
- **Rotate** makes a new QR code and stops the old one working. Do this if a code is lost or misused.
- Scanning an unknown, wrong or deactivated code shows a plain error and never reveals internal numbers.

### Audit log (**Audit log**)
A record of important actions: who did what, to what, when, and whether it worked. It covers account and role changes,
menu and price changes, table changes, order and payment steps, and credit limit, hold, charge, payment and reversal
steps. It cannot be edited from the application.

---

## If something goes wrong
- **Cannot sign in:** check the account name and password. After five wrong passwords the account locks; an
  administrator can unlock it. Too many failed attempts from one device also pauses sign-in there for a short time.
- **Order refused with "several orders in a short time":** wait the number of minutes shown, or ask staff.
- **Credit order refused:** the message shows the cost and what you have available. Choose another payment method, or
  ask the manager about your limit.
- **System unavailable:** use the staff-assisted fallback in `docs/deployment-and-operations.md`.
