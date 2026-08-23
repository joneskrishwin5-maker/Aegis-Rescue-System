# 🛡️ Aegis Rescue System 
**Team:** ElevateX | **Event:** Faraway Round 2 Software Submission

## 📖 What is Aegis?
When a natural disaster strikes, the internet and cell towers are usually the first things to fail. **Aegis Rescue System** is a two-part disaster management platform: an offline mobile app for victims, and a live web dashboard for rescue teams. 

It uses "Mesh Networking" (connecting phones directly to each other via Bluetooth/WiFi-Direct) so victims can route SOS alerts and their exact GPS locations to the rescue dashboard, even when they have zero internet connection.

---

## ✨ Key Features

### 1. 📡 Works Without Internet (Mesh Network)
* **How it works:** We used the Google Nearby Connections API. If you have no internet, your phone connects to another nearby phone, which connects to another, creating a chain. Your SOS message hops along this chain until it reaches a phone that has an internet connection, which then instantly pushes the alert to the cloud dashboard.

### 2. 💻 Live Command Center Dashboard
* **How it works:** While the app is for victims, the Web Dashboard is for the rescue teams. Once an SOS message escapes the offline mesh network, it appears instantly on the dashboard. Rescuers can see all victims plotted on a real-time map, color-coded by their triage priority, so they know exactly where to send help first.

### 3. 🎙️ Voice-Activated SOS (For Trapped Victims)
* **How it works:** If someone is trapped under debris and cannot use their hands, the app is always listening in the background. If they simply say the word **"Emergency"**, the app automatically sends a top-priority SOS with their exact location to the dashboard without them ever touching the screen.

### 4. 🗺️ Offline Maps
* **How it works:** Standard maps crash when the internet goes down. We built a system that saves (caches) the high-quality street map of your city to the phone's memory beforehand. Even in Airplane Mode, the map loads perfectly so victims can see their location.

### 5. 🏥 Smart Triage Priority
* **How it works:** Not all emergencies are the same. Users can choose their condition (e.g., Bleeding, Trapped, Safe but need transport). The app sorts these into Priority Levels (1 to 4) so the dashboard highlights the most critical, life-threatening cases for the rescue teams.

### 6. 🎨 Calm UI & Offline Survival Guide
* **How it works:** During a disaster, people panic. We designed a very smooth, simple, and calming interface (no sharp edges). It also includes a sliding side-menu with quick, 5-point survival steps for Earthquakes, Floods, Wildfires, and Cyclones that works completely offline.

---

## 🚀 How Judges Can Demo the System

**Step 1: The Quick Setup (Needs Internet once)**
1. Install the Android App on a phone.
2. Open it while connected to Wi-Fi so the GPS locks on and the map saves to the phone's memory (takes about 1 minute).
3. Click the profile icon and save a name and phone number.

**Step 2: Testing the Offline SOS**
1. **Turn OFF Wi-Fi and Mobile Data** (or turn on Airplane Mode).
2. Open the app. Notice the map still works perfectly!
3. Click the giant red **SOS** button.
4. Choose a condition (like "CRITICAL: Injured & Bleeding").
5. The app will immediately start scanning for nearby offline phones to pass your SOS message to.

**Step 3: Testing the Voice Trigger**
1. Stay totally offline on the main map screen.
2. Say the word **"Emergency"** clearly out loud.
3. The app will hear you and instantly broadcast a Priority 1 SOS through the mesh network!

**Step 4: Viewing the Rescue Dashboard**
1. Open the Web Dashboard. 
2. Whenever a mesh SOS successfully reaches a phone with internet, it instantly pops up on this dashboard with the victim's name, phone number, exact GPS location, and emergency priority level.

---

## 📂 Project Folders

This single link contains both parts of our project:
* **/Aegis-Android-App:** The code for the Android mobile app (The Mesh Nodes).
* **/Aegis-Web-Dashboard:** The code for the rescue team's live website (The Command Center).
