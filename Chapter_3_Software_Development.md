Chapter 2
2	Requirement Specification and Analysis
2.1	Epics
•	Epic 1: Core Networking Infrastructure (E1)
Description: As a system, I want to establish foundational peer-to-peer connection capabilities so that the application can function and communicate entirely without internet access.
User Story 1.1 - Device Discovery
As a system, I want my device to automatically discover other devices nearby using available wireless technology so that a communication network can be formed
Acceptance Criteria:
•	Given the app is open
•	When I enter a new area
•	Then the system should detect other users in range without manual pairing
User Story 1.2 - Protocol Switching
As a system, I want to intelligently switch between connection protocols based on distance and battery so that communication is efficient
Acceptance Criteria:
•	Given the battery is low
•	When a connection is maintained
•	Then the system switches to a low-energy protocol automatically
User Story 1.3 - Mesh Network Routing
As a system, I want messages to travel multiple hops through the mesh network so that I can reach people beyond my direct physical range
Acceptance Criteria:
•	Given I am sending a message to a distant user
•	When I press send
•	Then the message relays through intermediate devices to reach the destination
User Story 1.4 - Fallback Messaging
As a system, I want to send a standard carrier alert when no peer-to-peer network is available so that I can still broadcast critical alerts
Acceptance Criteria:
•	Given no mesh network is detected
•	When I send an urgent alert
•	Then the system falls back to standard SMS/carrier messaging
________________________________________
•	Epic 2: Messaging & Data Synchronization (E2)
Description: As a user, I want a reliable system for routing and storing messages so that my communication reaches others even in a decentralized network.
User Story 2.1 - Text Messaging
As a user, I want to send and receive text messages within the mesh network so that I can communicate with others
Acceptance Criteria:
•	Given I am connected to the mesh
•	When I type and send a text
•	Then the recipient should receive it with a correct timestamp
User Story 2.2 - Data Synchronization
As a system, I want messages to be stored locally and synchronized across devices so that data remains consistent even after disconnections
Acceptance Criteria:
•	Given two devices reconnect after being apart
•	When they sync
•	Then both devices should display the complete conversation history
User Story 2.3 - Delivery Status
As a user, I want to see the delivery status of my important messages so that I know if my SOS or help request was received
Acceptance Criteria:
•	Given I sent a message
•	When the recipient's device receives it
•	Then my screen should update the status to "Delivered"
User Story 2.4 - Intelligent Routing
As a system, I want to use intelligent algorithms to find the best path for message routing so that messages are delivered quickly and reliably
Acceptance Criteria:
•	Given multiple paths exist to a destination
•	When a message is sent
•	Then the system selects the most optimal route to ensure delivery
________________________________________
•	Epic 3: Emergency Features & Alerts (E3)
Description: As a user in distress, I want critical life-saving features and alerts so that I can instantly request help or warn others of danger.
User Story 3.1 - SOS Alert
As a user in distress, I want to trigger an SOS alert with one tap so that I can quickly call for help and share my location
Acceptance Criteria:
•	Given the app is open
•	When I press the prominent SOS button
•	Then an alert containing my location is broadcast to all nearby users
User Story 3.2 - Disaster Alerts
As a user, I want to receive official disaster alerts from government sources relevant to my location so that I am informed about imminent dangers
Acceptance Criteria:
•	Given an official warning is issued for my area
•	When I check the app
•	Then I should see the alert displayed prominently
User Story 3.3 - Incident Reporting
As a user, I want to report a real-time incident description with photos and location within 10km radius so that I can warn my local community of active hazards
Acceptance Criteria:
•	Given I witness a hazard
•	When I submit a report
•	Then other users in the vicinity receive a notification of the incident
•	And the report automatically disappears from the system after 24 hours and I have option to manually delete from my own report before it expires.
________________________________________
•	Epic 4: Community Coordination & Safety (E4)
Description: As a community member, I want tools to organize groups and share locations so that we can coordinate relief efforts effectively.
User Story 4.1 - Help Requests
As a user needing help, I want to broadcast a help request (e.g., "Medical", "Food") so that volunteers nearby can assist me
Acceptance Criteria:
•	Given I need assistance
•	When I select a category and post a request
•	Then available volunteers see my request on their feed
User Story 4.2 - Volunteer Availability
As a volunteer, I want to mark myself as "Available to Help" and see nearby help requests so that I can offer assistance efficiently
Acceptance Criteria:
•	Given I have button my status to "Available"
•	When a help request is posted nearby
•	Then I receive a notification to assist
User Story 4.3 - Location Sharing
As a user, I want to see the real-time locations of nearby users who have opted-in on the map so that I can coordinate with them
Acceptance Criteria:
•	Given users have agreed to share location
•	When I look at the map
•	Then I see markers representing their current positions
User Story 4.4 - Hyper-Local Community Chat 
As a user, I want to join a temporary, location-based chat room so that I can exchange information and coordinate with other people in my immediate vicinity. 
Acceptance Criteria:
•	Given I am in a disaster-affected zone
•	When I open the "Community" tab
•	Then I am automatically placed in a chat group with other users in my mesh range (or defined radius)
•	And I can send text messages that are visible to everyone in that specific zone

________________________________________
•	Epic 5: Maps (E5)
Description: As a traveler or resident, I want an intuitive interface with offline navigation data so that I can identify hazard zones and navigate safely.
User Story 5.1 - Offline Navigation
As a user, I want to view an offline map that shows my location and hazard zones so that I can navigate safely without internet
Acceptance Criteria:
•	Given I have no internet connection
•	When I open the map
•	Then I can still see the terrain, streets, and marked danger zones
________________________________________
•	Epic 6: Safety & Preparedness Knowledge Base (E6)
Description: As a user, I want an offline library of safety protocols so that I have access to verified survival information when the internet is down.
User Story 6.1 - Disaster-Specific Guidelines
As a user, I want to access specific safety guidelines for various disaster types so that I know how to react during these specific events without internet access
Acceptance Criteria:
•	Given I am offline
•	When I select "Flood Safety"
•	Then the app displays the correct survival guidelines
User Story 6.2 - First Aid Procedures
As a user, I want to view First Aid procedures so that I can provide immediate medical assistance to injured persons
Acceptance Criteria:
•	Given a medical emergency
•	When I access the First Aid section
•	Then step-by-step visual instructions are displayed

________________________________________
•	Epic 7: User Authentication and Account Management (E7)
Description: As a new user, I want to create an account with role-based access so that I can use HLDSN services and be routed to the correct dashboard based on my role.
User Story 7.1 - User Signup
As a new user, I want to create an account with email, password, and personal details so that I can access the app
Acceptance Criteria:
•	Given I open the app as a new user
•	When I complete the signup form with valid data (first name, last name, mobile, email, password)
•	Then a Firebase Auth account is created and my profile is stored in Firestore
•	And I am routed to the login screen upon successful signup
User Story 7.2 - User Login with Authentication
As a registered user, I want to log in with my email and password so that I can access my personalized dashboard
Acceptance Criteria:
•	Given I have valid credentials
•	When I enter email and password and tap Login
•	Then I am authenticated via Firebase Auth and routed to my role-based dashboard
•	And an error message appears if credentials are invalid
User Story 7.3 - Role-Based Navigation
As an authenticated user, I want to be automatically routed to the correct dashboard based on my role so that I see only relevant features
Acceptance Criteria:
•	Given I am logged in
•	When my role is admin
•	Then I see AdminDashboardActivity
•	And when my role is ngo_admin, I see NgoDashboardActivity
•	And when my role is user, I see HomePageActivity
User Story 7.4 - Password Recovery
As a user who forgot their password, I want to receive a password reset email so that I can regain access to my account
Acceptance Criteria:
•	Given I am on the login screen
•	When I tap "Forgot Password" and enter my email
•	Then a password reset email is sent to my inbox
•	And I can follow the link to set a new password

________________________________________
•	Epic 8: User Profile and Health Information Management (E8)
Description: As a user, I want to store and manage my personal, medical, and emergency contact information so that it can be accessed during disaster response.
User Story 8.1 - Basic Profile Information
As a user, I want to save my name, address, phone, and age so that my basic details are available in the system
Acceptance Criteria:
•	Given I open the profile form
•	When I enter name, address, phone, and age and tap Save
•	Then these details are stored in Firestore and displayed on my profile screen
User Story 8.2 - Medical Information Storage
As a user, I want to record my blood group, height, weight, allergies, and injuries so that first responders have critical medical information
Acceptance Criteria:
•	Given I am on the profile form
•	When I fill in blood group, height, weight, and list my allergies and injuries
•	Then this information is stored securely in Firestore
•	And it can be viewed by authorized emergency personnel
User Story 8.3 - Emergency Contact Management
As a user, I want to add multiple emergency contacts so that my family and emergency services can be notified quickly
Acceptance Criteria:
•	Given I am on the profile form
•	When I add emergency contact names and phone numbers
•	Then all contacts are stored in an array in Firestore
•	And the list is preserved even after app closes/reopens
User Story 8.4 - Profile Image Upload
As a user, I want to upload a profile photo so that my account is personalized and I am recognizable to other users
Acceptance Criteria:
•	Given I am on the profile form
•	When I select a photo from gallery or camera and tap Save
•	Then the image is uploaded via ImageKit to a CDN
•	And the image URL is stored in my Firestore profile
•	And the image displays on my profile using Glide image loading

________________________________________
•	Epic 9: Home Dashboard and Session Management (E9)
Description: As a logged-in user, I want a central hub that displays news, badges, and quick access to all app features so that I can quickly find what I need during a disaster.
User Story 9.1 - Home Dashboard Initialization
As a user, I want the home dashboard to load my active session and display notification badges so that I see important updates at a glance
Acceptance Criteria:
•	Given I open the app and log in
•	When I reach the home screen
•	Then my session is active, user role controls visible features
•	And notification badges show counts for messages, incidents, and SOS alerts
User Story 9.2 - News Carousel Display
As a user, I want to see a rotating carousel of disaster-related news on the home screen so that I stay informed of current events
Acceptance Criteria:
•	Given I am on the home screen
•	When the page loads
•	Then a news carousel auto-rotates every 5 seconds showing disaster headlines
•	And tapping a news item opens the full story
•	And the carousel falls back to cached content if the external API is unavailable
User Story 9.3 - Feature Entry Points
As a user, I want quick access buttons to major features (chat, incidents, safety, volunteer, profile) so that I can navigate efficiently
Acceptance Criteria:
•	Given I am on the home screen
•	When I see the navigation buttons
•	Then each button routes to its corresponding feature module
•	And the buttons are always accessible from the home screen
User Story 9.4 - Emergency Action Access
As a user in distress, I want prominent SOS and emergency action buttons on the home screen so that I can trigger help immediately
Acceptance Criteria:
•	Given I am on the home screen
•	When I need emergency help
•	Then the SOS button is prominent and accessible with a 3-second press-and-hold
•	And triggering SOS broadcasts my location and alert to all nearby users

________________________________________
•	Epic 10: NGO and Volunteer Coordination (E10)
Description: As a volunteer or NGO admin, I want to enroll in volunteer programs and manage volunteer and camp center operations so that I can coordinate disaster response effectively.
User Story 10.1 - Multi-Step Volunteer Enrollment
As a prospective volunteer, I want to complete a guided enrollment process with multiple steps so that I can join the volunteer network
Acceptance Criteria:
•	Given I am a registered user
•	When I tap "Become a Volunteer" on the home screen
•	Then I see a multi-step form: Basic Info (name, NGO selection) → Skills → Agreement → Success
•	And I can proceed to the next step only if the current step is valid
•	And upon completion, my volunteer application is submitted to Firestore
User Story 10.2 - Volunteer Skills and Availability
As a volunteer, I want to indicate my skills and availability so that NGOs can match me to appropriate tasks
Acceptance Criteria:
•	Given I am on the skills form during enrollment
•	When I select relevant skills (e.g., First Aid, Water Distribution, Debris Removal)
•	Then my selections are stored
•	And I can toggle "Available to Help" to indicate I am ready for assignments
User Story 10.3 - NGO Volunteer Approvals
As an NGO admin, I want to review and approve pending volunteer applications so that I can build a trusted volunteer network
Acceptance Criteria:
•	Given I am logged in as ngo_admin
•	When I access the "Manage Volunteers" screen
•	Then I see a list of pending volunteer applications
•	And I can approve or reject each application with a reason
•	And approved volunteers are notified immediately
User Story 10.4 - Camp Center Management
As an NGO admin, I want to create and manage relief camp center locations and details so that volunteers know where to go
Acceptance Criteria:
•	Given I am logged in as ngo_admin
•	When I tap "Add Camp Center"
•	Then I can enter camp name, GPS location (via osmdroid map picker), capacity, and services
•	And the camp is displayed on the NGO map for all volunteers to see
•	And I can update or delete camp details at any time
User Story 10.5 - Volunteer Location Tracking
As an NGO admin, I want to see real-time locations of active volunteers on a map so that I can coordinate relief operations
Acceptance Criteria:
•	Given I am logged in as ngo_admin
•	When I open the volunteer network map
•	Then I see markers representing each volunteer's current location
•	And the markers update in real-time as volunteers move
•	And markers show volunteer name and current availability status

________________________________________
•	Epic 11: Administrative Control and System Governance (E11)
Description: As a system administrator, I want to manage users, NGOs, and NGO registration requests so that the platform maintains data integrity and security.
User Story 11.1 - User Management
As an admin, I want to view and manage all user accounts in the system so that I can maintain data quality and handle user issues
Acceptance Criteria:
•	Given I am logged in as admin
•	When I access the "Manage Users" screen
•	Then I see a list of all registered users with their details and roles
•	And I can search, filter, or sort users
•	And I can view or delete user accounts as needed
User Story 11.2 - NGO Management
As an admin, I want to view and manage all registered NGOs so that I can ensure compliance and remove inactive organizations
Acceptance Criteria:
•	Given I am logged in as admin
•	When I access the "Manage NGOs" screen
•	Then I see all active NGOs with their details and registration dates
•	And I can approve, reject, or remove NGOs from the platform
User Story 11.3 - NGO Registration Request Review
As an admin, I want to review pending NGO registration requests and approve or reject them so that only legitimate organizations operate on the platform
Acceptance Criteria:
•	Given I am logged in as admin
•	When I access the "Manage NGO Requests" screen
•	Then I see pending NGO registration applications
•	And I can review request details and approve with automatic confirmation to the NGO
•	And I can reject with a reason provided to the applicant
•	And request decisions are logged with timestamp and admin reviewer info
User Story 11.4 - Incident and Report Moderation
As an admin, I want to review and moderate user-submitted incidents and reports so that I can remove false or harmful content
Acceptance Criteria:
•	Given I am logged in as admin
•	When I access the incident moderation panel
•	Then I see reported incidents with flags and user feedback
•	And I can mark incidents as verified or remove them if false/harmful
•	And moderation actions are logged with reason

2.2	Test cases
•	Epic 1: Core Networking Infrastructure
•	Test Case 1
Epic:	E1 – Core Networking Infrastructure
Test ID:	E1US1TC1
User Story ID:	US1.1 – As a system, I want my device to automatically discover other devices nearby using available wireless technology so that a communication network can be formed.
Test Case:	Verify that nearby devices are automatically discovered when the app is opened.
Input:	App is opened; Bluetooth and WiFi Direct enabled; another HLDSN device is nearby.
Expected Result:	The other device appears in the “Nearby Devices” list without manual pairing; discovery notification shown.
________________________________________
•	Test Case 2
Epic:	E1 – Core Networking Infrastructure
Test ID:	E1US1TC2
User Story ID:	US1.1 – As a system, I want my device to automatically discover other devices nearby using available wireless technology so that a communication network can be formed.
Test Case:	Verify no devices appear when no peers are in range.
Input:	App is opened; Bluetooth and WiFi are ON; no HLDSN devices are within range.
Expected Result:	UI shows “No devices found” and continues scanning; no crash or freeze.
________________________________________
•	Test Case 3
Epic:	E1 – Core Networking Infrastructure
Test ID:	E1US2TC1
User Story ID:	US1.2 – As a system, I want to intelligently switch between connection protocols based on distance and battery so that communication is efficient.
Test Case:	Verify automatic switch to BLE when battery is low.
Input:	Battery below threshold (e.g., 20%); device currently in WiFi Direct connection.
Expected Result:	System switches to BLE automatically and continues messaging; log/indicator shows “Switched to BLE due to battery”.
________________________________________
•	Test Case 4
Epic:	E1 – Core Networking Infrastructure
Test ID:	E1US2TC2
User Story ID:	US1.2 – As a system, I want to intelligently switch between connection protocols based on distance and battery so that communication is efficient.
Test Case:	Verify WiFi Direct is selected for large file transfers.
Input:	Message/file size > 10 KB; peer within WiFi Direct range; battery > threshold.
Expected Result:	System uses WiFi Direct for transfer; file completes successfully.
________________________________________
•	Test Case 5
Epic:	E1 – Core Networking Infrastructure
Test ID:	E1US3TC1
User Story ID:	US1.3 – As a system, I want messages to travel multiple hops through the mesh network so that I can reach people beyond my direct physical range.
Test Case:	Verify multihop routing (A → B → C).
Input:	Device A sends a message to Device C; A cannot reach C directly; B is between them.
Expected Result:	B relays the message; C receives it with correct sender and timestamp.
________________________________________
•	Test Case 6
Epic:	E1 – Core Networking Infrastructure
Test ID:	E1US3TC2
User Story ID:	US1.3 – As a system, I want messages to travel multiple hops through the mesh network so that I can reach people beyond my direct physical range.
Test Case:	Verify failure behaviour if no multihop path exists.
Input:	A sends to C but no intermediate nodes available.
Expected Result:	Message marked “Not Delivered”; app retries per policy and reports “Delivery failed” after retries.
________________________________________
•	Test Case 7
Epic:	E1 – Core Networking Infrastructure
Test ID:	E1US3TC3
User Story ID:	US1.3 – As a system, I want messages to travel multiple hops through the mesh network so that I can reach people beyond my direct physical range.
Test Case:	Verify node failure detection and rerouting.
Input:	Mesh path A→B→C exists; B goes offline while A attempts to send to C.
Expected Result:	System discovers failure, finds alternate route (if available), and delivers message; if no route, informs sender.
________________________________________
•	Test Case 8
Epic:	E1 – Core Networking Infrastructure
Test ID:	E1US4TC1
User Story ID:	US1.4 – As a system, I want to send a standard carrier alert when no peer-to-peer network is available so that I can still broadcast critical alerts.
Test Case:	Verify SMS fallback is used when no mesh peers detected.
Input:	No mesh peers in range; user triggers SOS.
Expected Result:	App sends SMS fallback containing message contents and GPS coordinates; shows confirmation “Alert sent via SMS”.
________________________________________
•	Test Case 9
Epic:	E1 – Core Networking Infrastructure
Test ID:	E1US4TC2
User Story ID:	US1.4 – As a system, I want to send a standard carrier alert when no peer-to-peer network is available so that I can still broadcast critical alerts.
Test Case:	Verify proper user notice if SMS fallback fails (no carrier/sms permission).
Input:	No peers; SMS permission denied; user triggers SOS.
Expected Result:	App displays “SMS fallback unavailable — enable SMS permission or seek alternate contact”; no crash.
________________________________________
•	Epic 2: Messaging & Data Synchronization
•	Test Case 10
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US1TC1
User Story ID:	US2.1 – As a user, I want to send and receive text messages within the mesh network so that I can communicate with others.
Test Case:	Verify direct P2P text message delivery.
Input:	Device A and Device B in direct mesh range; A sends “Hello”.
Expected Result:	B receives message within target latency (≤1–3s); shows correct timestamp and “Delivered” status.
________________________________________
•	Test Case 11
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US1TC2
User Story ID:	US2.1 – As a user, I want to send and receive text messages within the mesh network so that I can communicate with others.
Test Case:	Verify empty message is blocked.
Input:	User taps Send with empty text field.
Expected Result:	App shows validation “Message cannot be empty”; no message sent.
________________________________________
•	Test Case 12
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US1TC3
User Story ID:	US2.1 – As a user, I want to send and receive text messages within the mesh network so that I can communicate with others.
Test Case:	Verify push notification arrives when app is backgrounded.
Input:	User B sends message while A’s app is backgrounded.
Expected Result:	A receives a push notification with message preview; tapping opens chat.
________________________________________
•	Test Case 13
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US2TC1
User Story ID:	US2.2 – As a system, I want messages to be stored locally and synchronized across devices so that data remains consistent even after disconnections.
Test Case:	Verify message sync after reconnection preserves causal order.
Input:	A and B exchange offline messages while disconnected; later they reconnect.
Expected Result:	Both show full conversation history; causal order preserved (messages appear in logical flow of conversation, not just based on sync time).
________________________________________
•	Test Case 14
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US2TC2
User Story ID:	US2.2 – As a system, I want messages to be stored locally and synchronized across devices so that data remains consistent even after disconnections.
Test Case:	Verify message order when devices A and B create messages offline independently and then sync.
Input:	A sends Msg1 (offline); B sends Msg2 (offline); Devices reconnect.
Expected Result:	Messages merge into the timeline based on their original creation timestamps; no messages are lost.
________________________________________
•	Test Case 15
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US2TC3
User Story ID:	US2.2 – As a system, I want messages to be stored locally and synchronized across devices so that data remains consistent even after disconnections.
Test Case:	Verify duplicate messages (same ID) are discarded during sync.
Input:	Device receives the same message packet twice due to network lag.
Expected Result:	The database discards the duplicate ID; only one copy of the message is displayed in the UI.
________________________________________
•	Test Case 16
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US2TC4
User Story ID:	US2.2 – As a system, I want messages to be stored locally and synchronized across devices so that data remains consistent even after disconnections.
Test Case:	Verify queued messages maintain original timestamps.
Input:	Messages composed offline then synced.
Expected Result:	Messages show original compose timestamps (not the time the sync occurred).
________________________________________
•	Test Case 17
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US2TC5
User Story ID:	US2.2 – As a system, I want messages to be stored locally and synchronized across devices so that data remains consistent even after disconnections.
Test Case:	Verify message history retained after app close/reopen.
Input:	Send messages, close app, reopen app.
Expected Result:	Full history visible; no missing messages.
________________________________________
•	Test Case 18
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US3TC1
User Story ID:	US2.3 – As a user, I want to see the delivery status of my important messages so that I know if my SOS or help request was received.
Test Case:	Verify status lifecycle (Sent → Delivered → Read).
Input:	Send message to reachable peer; peer opens message.
Expected Result:	Sender sees statuses transition appropriately; time stamps for each state visible.
________________________________________
•	Test Case 19
Epic:	E2 — Messaging & Data Synchronization
Test ID:	E2US4TC1
User Story ID:	US2.4 – As a system, I want to use intelligent algorithms to find the best path for message routing so that messages are delivered quickly and reliably.
Test Case:	Verify automatic retries for transient failures.
Input:	Network flaps while sending; send fails temporarily.
Expected Result:	System retries per policy (e.g., 3 attempts at 5-second intervals) and delivers when connection returns.
________________________________________
•	Epic 3: Emergency Features & Alerts
•	Test Case 20
Epic:	E3 — Emergency Features & Alerts
Test ID:	E3US1TC1
User Story ID:	US3.1 – As a user in distress, I want to trigger an SOS alert with one tap so that I can quickly call for help and share my location.
Test Case:	Verify SOS requires a 3-second press-and-hold to prevent accidental triggers.
Input:	User presses and holds SOS button for 3 seconds.
Expected Result:	Visual indicator fills up; SOS triggers only after full 3-second hold; accidental taps are ignored.
________________________________________
•	Test Case 21
Epic:	E3 — Emergency Features & Alerts
Test ID:	E3US1TC2
User Story ID:	US3.1 – As a user in distress, I want to trigger an SOS alert with one tap so that I can quickly call for help and share my location.
Test Case:	Verify SOS works when device is offline (mesh present).
Input:	No internet; mesh peers available; user triggers SOS.
Expected Result:	SOS broadcast via mesh to peers; fallback to SMS if no mesh peers.
________________________________________
•	Test Case 22
Epic:	E3 — Emergency Features & Alerts
Test ID:	E3US1TC3
User Story ID:	US3.1 – As a user in distress, I want to trigger an SOS alert with one tap so that I can quickly call for help and share my location.
Test Case:	Measure SOS propagation latency.
Input:	Trigger SOS and measure timestamp differences across peers.
Expected Result:	SOS propagation meets SLA (e.g., delivered to immediate peers within 10s).
________________________________________
•	Test Case 23
Epic:	E3 — Emergency Features & Alerts
Test ID:	E3US2TC1
User Story ID:	US3.2 – As a user, I want to receive official disaster alerts from government sources relevant to my location so that I am informed about imminent dangers.
Test Case:	Verify official alert integration displays alerts filtered by location.
Input:	Test alert pushed for user’s region (simulate).
Expected Result:	Alert appears prominently with severity, source, and timestamp; color coded by severity.
________________________________________
•	Test Case 24
Epic:	E3 — Emergency Features & Alerts
Test ID:	E3US2TC2
User Story ID:	US3.2 – As a user, I want to receive official disaster alerts from government sources relevant to my location so that I am informed about imminent dangers.
Test Case:	Verify expired alerts are not displayed.
Input:	System receives an alert packet with an expiration timestamp in the past.
Expected Result:	The expired alert is automatically filtered out and not shown to the user.
________________________________________
•	Test Case 25
Epic:	E3 — Emergency Features & Alerts
Test ID:	E3US3TC1
User Story ID:	US3.3 – As a user, I want to report a real-time incident description so that I can warn others in the network.
Test Case:	Verify incident reporting with severity works and notifications propagate.
Input:	User submits incident with description and severity level.
Expected Result:	Incident visible in feed and map overlay; nearby users receive notification; incident metadata saved.
________________________________________
•	Epic 4: Community Coordination & Safety
•	Test Case 26
Epic:	E4 — Community Coordination & Safety
Test ID:	E4US1TC1
User Story ID:	US4.1 – As a user needing help, I want to broadcast a help request (e.g., "Medical", "Food") so that volunteers nearby can assist me.
Test Case:	Verify help request broadcast to nearby volunteers.
Input:	User selects “Medical” and sends help request.
Expected Result:	Nearby volunteers receive notification with location and details; request appears in volunteer feed.
________________________________________
•	Test Case 27
Epic:	E4 — Community Coordination & Safety
Test ID:	E4US1TC2
User Story ID:	US4.1 – As a user needing help, I want to broadcast a help request (e.g., "Medical", "Food") so that volunteers nearby can assist me.
Test Case:	Verify multiple help requests from same user are handled correctly.
Input:	User posts "Need Water", then immediately posts "Need Medical".
Expected Result:	System treats them as distinct requests OR updates the existing status (depending on policy); prevents spamming duplicate identical requests.
________________________________________
•	Test Case 28
Epic:	E4 — Community Coordination & Safety
Test ID:	E4US2TC1
User Story ID:	US4.2 – As a volunteer, I want to mark myself as "Available to Help" and see nearby help requests so that I can offer assistance efficiently.
Test Case:	Verify availability button and request deliveries.
Input:	Volunteer toggles status to “Available”; a help request is posted within radius.
Expected Result:	Volunteer gets notification and can accept/decline; UI shows distance to request.
________________________________________
•	Test Case 29
Epic:	E4 — Community Coordination & Safety
Test ID:	E4US2TC2
User Story ID:	US4.2 – As a volunteer, I want to mark myself as "Available to Help" and see nearby help requests so that I can offer assistance efficiently.
Test Case:	Verify help request includes supply category and volunteers filter by capability.
Input:	Help request includes “Need Water + Medical” category.
Expected Result:	Volunteers with either "Water" OR "Medical" capability flag receive the prioritized notification.
________________________________________
•	Test Case 30
Epic:	E4 — Community Coordination & Safety
Test ID:	E4US2TC3
User Story ID:	US4.2 – As a volunteer, I want to mark myself as "Available to Help" and see nearby help requests so that I can offer assistance efficiently.
Test Case:	Verify help request status update to “Resolved”.
Input:	Volunteer marks request as resolved after assisting.
Expected Result:	Request status updated; originator and nearby users notified; request removed from active list.
________________________________________
•	Test Case 31
Epic:	E4 — Community Coordination & Safety
Test ID:	E4US3TC1
User Story ID:	US4.3 – As a user, I want to see the real-time locations of nearby users who have opted-in on the map so that I can coordinate with them.
Test Case:	Verify realtime opt-in location sharing markers.
Input:	Two users enable “Share location” and open map.
Expected Result:	Each sees markers for the other that update periodically.
________________________________________
•	Test Case 32
Epic:	E4 — Community Coordination & Safety
Test ID:	E4US3TC2
User Story ID:	US4.3 – As a user, I want to see the real-time locations of nearby users who have opted-in on the map so that I can coordinate with them.
Test Case:	Verify location sharing stops when user opts out.
Input:	User disables “Share Location”.
Expected Result:	User's marker disappears from other peers' maps immediately (or upon next refresh).
________________________________________
•	Epic 5: Maps
•	Test Case 33
Epic:	E5 — Maps
Test ID:	E5US1TC1
User Story ID:	US5.1 – As a user, I want to view an offline map that shows my location and hazard zones so that I can navigate safely without internet.
Test Case:	Verify offline map loads and hazard overlays display.
Input:	Device offline; open Map screen; offline map data available.
Expected Result:	Map renders; hazard zones shown color-coded; user location displayed.
________________________________________
•	Test Case 34
Epic:	E5 — Maps
Test ID:	E5US1TC2
User Story ID:	US5.1 – As a user, I want to view an offline map that shows my location and hazard zones so that I can navigate safely without internet.
Test Case:	Verify map panning and zoom working offline.
Input:	User pans/zooms map offline.
Expected Result:	Tiles loaded from local store; no placeholder tiles; smooth interaction.
________________________________________
•	Test Case 35
Epic:	E5 — Maps
Test ID:	E5US1TC3
User Story ID:	US5.1 – As a user, I want to view an offline map that shows my location and hazard zones so that I can navigate safely without internet.
Test Case:	Verify adding and sharing relief points.
Input:	User pins a relief center and marks details, then shares.
Expected Result:	Pin visible on local and synced maps; nearby users receive update when sync occurs.
________________________________________
•	Test Case 36
Epic:	E5 — Maps
Test ID:	E5US1TC4
User Story ID:	US5.1 – As a user, I want to view an offline map that shows my location and hazard zones so that I can navigate safely without internet.
Test Case:	Verify duplicate pin handling.
Input:	User A and User B add the same relief point (same coordinates) simultaneously.
Expected Result:	System detects proximity overlap and merges them into one pin, or asks user to confirm if it is a duplicate.
________________________________________
•	Epic 6: Safety & Preparedness Knowledge Base
•	Test Case 37
Epic:	E6 — Safety & Preparedness Knowledge Base
Test ID:	E6US1TC1
User Story ID:	US6.1 – As a user, I want to access specific safety guidelines for various disaster types so that I know how to react during these specific events without internet access.
Test Case:	Verify knowledge base content loads offline.
Input:	Device offline; open “Flood Safety” content.
Expected Result:	Content loads fully; images and steps present; no broken links.
________________________________________
•	Test Case 38
Epic:	E6 — Safety & Preparedness Knowledge Base
Test ID:	E6US1TC2
User Story ID:	US6.1 – As a user, I want to access specific safety guidelines for various disaster types so that I know how to react during these specific events without internet access.
Test Case:	Verify search within offline content.
Input:	User searches “first aid” while offline.
Expected Result:	Relevant local articles found and displayed.
________________________________________
•	Test Case 39
Epic:	E6 — Safety & Preparedness Knowledge Base
Test ID:	E6US2TC1
User Story ID:	US6.2 – As a user, I want to view First Aid procedures so that I can provide immediate medical assistance to injured persons.
Test Case:	Verify first aid guide displays properly.
Input:	Open “CPR” guide; step through instructions.
Expected Result:	Steps displayed with images/icons; navigation between steps works.
________________________________________
•	Epic 7: User Authentication and Account Management
•	Test Case 40
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US1TC1
User Story ID:	US7.1 – As a new user, I want to create an account with email and password so that I can access the app.
Test Case:	Verify successful account creation with valid signup data.
Input:	Enter first name, last name, mobile, email, password, confirm password; accept terms; tap Signup.
Expected Result:	Firebase Auth account created; Firestore users/{uid} document created with firstName, lastName, mobile, email, role, createdAt; user routed to login screen.
________________________________________
•	Test Case 41
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US1TC2
User Story ID:	US7.1 – As a new user, I want to create an account with email and password so that I can access the app.
Test Case:	Verify signup validation blocks invalid data.
Input:	Leave required fields empty or enter mismatched passwords; tap Signup.
Expected Result:	Validation error displayed; account not created; no navigation.
________________________________________
•	Test Case 42
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US1TC3
User Story ID:	US7.1 – As a new user, I want to create an account with email and password so that I can access the app.
Test Case:	Verify signup prevents duplicate email registration.
Input:	Attempt to signup with an email already registered in Firebase.
Expected Result:	Error message shown: "Email already in use"; account creation blocked.
________________________________________
•	Test Case 43
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US2TC1
User Story ID:	US7.2 – As a registered user, I want to log in with my email and password so that I can access my personalized dashboard.
Test Case:	Verify successful login with correct credentials.
Input:	Enter valid email and password; tap Login.
Expected Result:	User authenticated via Firebase Auth; mesh identity initialized; user routed to appropriate dashboard based on role.
________________________________________
•	Test Case 44
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US2TC2
User Story ID:	US7.2 – As a registered user, I want to log in with my email and password so that I can access my personalized dashboard.
Test Case:	Verify login fails with incorrect password.
Input:	Enter valid email but wrong password; tap Login.
Expected Result:	Toast message shown: "Login Failed: Invalid credentials"; no navigation; login form remains open.
________________________________________
•	Test Case 45
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US3TC1
User Story ID:	US7.3 – As an authenticated user, I want to be automatically routed to the correct dashboard based on my role.
Test Case:	Verify admin users are routed to AdminDashboardActivity.
Input:	Login with account where role = "admin".
Expected Result:	AdminDashboardActivity opens; user sees admin management options (Manage Users, Manage NGOs, Manage Requests).
________________________________________
•	Test Case 46
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US3TC2
User Story ID:	US7.3 – As an authenticated user, I want to be automatically routed to the correct dashboard based on my role.
Test Case:	Verify ngo_admin users are routed to NgoDashboardActivity.
Input:	Login with account where role = "ngo_admin".
Expected Result:	NgoDashboardActivity opens; user sees NGO options (Manage Volunteers, View Volunteers, Add Camp Center, Manage Camps).
________________________________________
•	Test Case 47
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US3TC3
User Story ID:	US7.3 – As an authenticated user, I want to be automatically routed to the correct dashboard based on my role.
Test Case:	Verify regular users are routed to HomePageActivity.
Input:	Login with account where role = "user".
Expected Result:	HomePageActivity opens; user sees home dashboard with news carousel, quick access buttons, and badges.
________________________________________
•	Test Case 48
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US4TC1
User Story ID:	US7.4 – As a user who forgot their password, I want to receive a password reset email so that I can regain access.
Test Case:	Verify password reset email is sent.
Input:	On login screen, tap "Forgot Password"; enter registered email; wait for email.
Expected Result:	Password reset email sent to inbox; email contains reset link; user can follow link to set new password.
________________________________________
•	Test Case 49
Epic:	E7 — User Authentication and Account Management
Test ID:	E7US4TC2
User Story ID:	US7.4 – As a user who forgot their password, I want to receive a password reset email so that I can regain access.
Test Case:	Verify error when attempting password reset with unregistered email.
Input:	Tap "Forgot Password"; enter non-existent email address.
Expected Result:	System either silently succeeds (security best practice) or shows "If an account exists, reset email will be sent"; user is not informed whether email exists or not.
________________________________________
•	Epic 8: User Profile and Health Information Management
•	Test Case 50
Epic:	E8 — User Profile and Health Information Management
Test ID:	E8US1TC1
User Story ID:	US8.1 – As a user, I want to save my basic profile information so that my details are available in the system.
Test Case:	Verify basic profile information is saved to Firestore.
Input:	Open profile form; enter name, address, phone, age; tap Save.
Expected Result:	Data saved to Firestore users/{uid} document; UserProfileActivity displays the saved information correctly.
________________________________________
•	Test Case 51
Epic:	E8 — User Profile and Health Information Management
Test ID:	E8US1TC2
User Story ID:	US8.1 – As a user, I want to save my basic profile information so that my details are available in the system.
Test Case:	Verify profile data persists after app close/reopen.
Input:	Save profile data; close app; reopen and navigate to profile screen.
Expected Result:	Previously saved data is displayed; no data loss; Firestore remains source of truth.
________________________________________
•	Test Case 52
Epic:	E8 — User Profile and Health Information Management
Test ID:	E8US2TC1
User Story ID:	US8.2 – As a user, I want to record medical information so that first responders have critical health data.
Test Case:	Verify medical data array fields (allergies, injuries) are stored as arrays.
Input:	Enter blood group, height, weight; add allergies (Penicillin, Shellfish); add injuries (Previous fractures); tap Save.
Expected Result:	Data stored in Firestore; allergies and injuries stored as arrays; data retrievable and modifiable later.
________________________________________
•	Test Case 53
Epic:	E8 — User Profile and Health Information Management
Test ID:	E8US2TC2
User Story ID:	US8.2 – As a user, I want to record medical information so that first responders have critical health data.
Test Case:	Verify medical data can be updated without affecting other fields.
Input:	Update blood group only; tap Save.
Expected Result:	Only blood group field updated; other fields (height, weight, allergies, injuries) remain unchanged.
________________________________________
•	Test Case 54
Epic:	E8 — User Profile and Health Information Management
Test ID:	E8US3TC1
User Story ID:	US8.3 – As a user, I want to add emergency contacts so that my family and services can be notified quickly.
Test Case:	Verify emergency contacts are stored as array in Firestore.
Input:	Add 3 emergency contacts with names and phone numbers; tap Save.
Expected Result:	All 3 contacts stored in array field; each contact retrievable individually; array order preserved.
________________________________________
•	Test Case 55
Epic:	E8 — User Profile and Health Information Management
Test ID:	E8US3TC2
User Story ID:	US8.3 – As a user, I want to add emergency contacts so that my family and services can be notified quickly.
Test Case:	Verify emergency contact list can be modified.
Input:	Add 2 contacts; save; reopen form; modify one contact; delete another; add a new one; save.
Expected Result:	Final array contains 2 contacts (modified + new); deleted contact removed; order consistent.
________________________________________
•	Test Case 56
Epic:	E8 — User Profile and Health Information Management
Test ID:	E8US4TC1
User Story ID:	US8.4 – As a user, I want to upload a profile photo so that my account is personalized.
Test Case:	Verify image upload via ImageKit and URL storage.
Input:	Select image from gallery; authenticate with ImageKit backend; tap Save.
Expected Result:	Image uploaded successfully; profileImageUrl stored in Firestore; image displayed on profile via Glide.
________________________________________
•	Test Case 57
Epic:	E8 — User Profile and Health Information Management
Test ID:	E8US4TC2
User Story ID:	US8.4 – As a user, I want to upload a profile photo so that my account is personalized.
Test Case:	Verify image upload timeout and fallback handling.
Input:	Attempt image upload with slow network or timeout; observe timeout behavior.
Expected Result:	Timeout detected; user notified "Image upload failed"; able to retry or skip; profile saves without image if skipped.
________________________________________
•	Test Case 58
Epic:	E8 — User Profile and Health Information Management
Test ID:	E8US4TC3
User Story ID:	US8.4 – As a user, I want to upload a profile photo so that my account is personalized.
Test Case:	Verify image is loaded from Glide cache on profile view.
Input:	Upload profile image; close profile form; reopen profile form.
Expected Result:	Image displays quickly from cache; no re-download if already cached.
________________________________________
•	Epic 9: Home Dashboard and Session Management
•	Test Case 59
Epic:	E9 — Home Dashboard and Session Management
Test ID:	E9US1TC1
User Story ID:	US9.1 – As a user, I want the home dashboard to load my active session and badges.
Test Case:	Verify home dashboard initializes with active session and badge counts.
Input:	Login and reach HomePageActivity.
Expected Result:	User session is active; notification badges display (message count, incident count, SOS alerts); all counts are accurate.
________________________________________
•	Test Case 60
Epic:	E9 — Home Dashboard and Session Management
Test ID:	E9US1TC2
User Story ID:	US9.1 – As a user, I want the home dashboard to load my active session and badges.
Test Case:	Verify badges update in real-time when new incidents or messages arrive.
Input:	Receive a new incident notification while on home screen.
Expected Result:	Incident badge count increments immediately; visual feedback (animation or color change) shown.
________________________________________
•	Test Case 61
Epic:	E9 — Home Dashboard and Session Management
Test ID:	E9US2TC1
User Story ID:	US9.2 – As a user, I want a rotating carousel of disaster news on the home screen.
Test Case:	Verify news carousel auto-rotates and transitions smoothly.
Input:	Open home screen and observe carousel for at least 15 seconds.
Expected Result:	Carousel auto-advances every 5 seconds; transition is smooth; each slide displays news headline and image.
________________________________________
•	Test Case 62
Epic:	E9 — Home Dashboard and Session Management
Test ID:	E9US2TC2
User Story ID:	US9.2 – As a user, I want a rotating carousel of disaster news on the home screen.
Test Case:	Verify carousel fallback when external API fails.
Input:	Disable internet connection; reload home screen.
Expected Result:	Carousel displays cached or dummy news items; no crash; fallback message shown if offline.
________________________________________
•	Test Case 63
Epic:	E9 — Home Dashboard and Session Management
Test ID:	E9US2TC3
User Story ID:	US9.2 – As a user, I want a rotating carousel of disaster news on the home screen.
Test Case:	Verify tapping a carousel item opens full news story.
Input:	Tap on a carousel news item.
Expected Result:	NewsDetailActivity opens with full story headline, image, description, and source.
________________________________________
•	Test Case 64
Epic:	E9 — Home Dashboard and Session Management
Test ID:	E9US3TC1
User Story ID:	US9.3 – As a user, I want quick access buttons to major features.
Test Case:	Verify all feature entry point buttons are functional.
Input:	From home screen, tap Chat, Incidents, Safety, Volunteer, Profile buttons.
Expected Result:	Each button routes to correct activity; no crashes; navigation is instantaneous.
________________________________________
•	Test Case 65
Epic:	E9 — Home Dashboard and Session Management
Test ID:	E9US4TC1
User Story ID:	US9.4 – As a user in distress, I want prominent SOS button on home screen.
Test Case:	Verify SOS button requires 3-second press-and-hold to trigger.
Input:	Press SOS button for 1 second (release); observe no trigger.
Expected Result:	Button shows visual fill indicator; SOS does not trigger until 3-second threshold reached.
________________________________________
•	Test Case 66
Epic:	E9 — Home Dashboard and Session Management
Test ID:	E9US4TC2
User Story ID:	US9.4 – As a user in distress, I want prominent SOS button on home screen.
Test Case:	Verify SOS broadcasts location when triggered.
Input:	Press and hold SOS for 3 seconds; allow broadcast to complete.
Expected Result:	SOS alert sent with user location; online path uses Firestore; offline path uses BLE/WiFi Direct; feedback shown to user.
________________________________________
•	Epic 10: NGO and Volunteer Coordination
•	Test Case 67
Epic:	E10 — NGO and Volunteer Coordination
Test ID:	E10US1TC1
User Story ID:	US10.1 – As a volunteer, I want to complete a multi-step enrollment process.
Test Case:	Verify volunteer enrollment flow completes successfully.
Input:	Start volunteer enrollment; complete Basic Form (NGO selection); Skills Form (select skills); Agreement (accept); finish.
Expected Result:	Each step validates before proceeding; final submission stores volunteer application in Firestore; success screen displayed.
________________________________________
•	Test Case 68
Epic:	E10 — NGO and Volunteer Coordination
Test ID:	E10US1TC2
User Story ID:	US10.1 – As a volunteer, I want to complete a multi-step enrollment process.
Test Case:	Verify enrollment form can be saved as draft and resumed.
Input:	Complete Basic Form; navigate away; return to volunteer module; tap "Continue Enrollment".
Expected Result:	Previously filled data is preserved (Intent extras or SharedPreferences); user can resume from last step.
________________________________________
•	Test Case 69
Epic:	E10 — NGO and Volunteer Coordination
Test ID:	E10US2TC1
User Story ID:	US10.2 – As a volunteer, I want to indicate skills and availability.
Test Case:	Verify volunteer skills are stored and available status is toggleable.
Input:	During enrollment, select First Aid and Water Distribution; after enrollment, toggle "Available to Help" on/off.
Expected Result:	Skills stored in volunteer application; availability toggle persists; NGO dashboard reflects current availability status.
________________________________________
•	Test Case 70
Epic:	E10 — NGO and Volunteer Coordination
Test ID:	E10US3TC1
User Story ID:	US10.3 – As an NGO admin, I want to review and approve volunteer applications.
Test Case:	Verify pending volunteer applications display and approval persists.
Input:	Login as ngo_admin; access "Manage Volunteers"; see pending applications; approve one; reject one with reason.
Expected Result:	Approved volunteer's status updated to "approved"; rejected volunteer notified with reason; list updates; changes persisted in Firestore.
________________________________________
•	Test Case 71
Epic:	E10 — NGO and Volunteer Coordination
Test ID:	E10US3TC2
User Story ID:	US10.3 – As an NGO admin, I want to review and approve volunteer applications.
Test Case:	Verify approved volunteers receive notification.
Input:	Approve a pending volunteer application as NGO admin.
Expected Result:	Notification sent to volunteer's device; volunteer can accept or decline; acceptance routed to NGO dashboard.
________________________________________
•	Test Case 72
Epic:	E10 — NGO and Volunteer Coordination
Test ID:	E10US4TC1
User Story ID:	US10.4 – As an NGO admin, I want to manage camp center locations.
Test Case:	Verify camp center creation with location picker stores all data.
Input:	Login as ngo_admin; tap "Add Camp Center"; fill name, select location on osmdroid map, enter capacity and services; save.
Expected Result:	Camp center document created in Firestore; location (lat/lng) stored; camp visible on NGO map; volunteers can see camp on their map.
________________________________________
•	Test Case 73
Epic:	E10 — NGO and Volunteer Coordination
Test ID:	E10US4TC2
User Story ID:	US10.4 – As an NGO admin, I want to manage camp center locations.
Test Case:	Verify camp center can be updated or deleted.
Input:	Access camp management; modify camp capacity; delete another camp.
Expected Result:	Modified camp reflects new capacity; deleted camp removed from Firestore and maps; changes visible to all users.
________________________________________
•	Test Case 74
Epic:	E10 — NGO and Volunteer Coordination
Test ID:	E10US5TC1
User Story ID:	US10.5 – As an NGO admin, I want to see real-time volunteer locations on a map.
Test Case:	Verify volunteer location markers update in real-time on NGO map.
Input:	Login as ngo_admin; open volunteer network map; observe volunteer markers; move to different location as volunteer.
Expected Result:	All active volunteers shown as markers; markers update as volunteers move; marker shows name and availability status.
________________________________________
•	Test Case 75
Epic:	E10 — NGO and Volunteer Coordination
Test ID:	E10US5TC2
User Story ID:	US10.5 – As an NGO admin, I want to see real-time volunteer locations on a map.
Test Case:	Verify inactive volunteers do not appear on NGO map.
Input:	A volunteer toggles "Available to Help" OFF; observe map as NGO admin.
Expected Result:	Volunteer marker disappears or shows different status; map only shows currently active volunteers.
________________________________________
•	Epic 11: Administrative Control and System Governance
•	Test Case 76
Epic:	E11 — Administrative Control and System Governance
Test ID:	E11US1TC1
User Story ID:	US11.1 – As an admin, I want to view and manage all user accounts.
Test Case:	Verify admin can view all users with search and filter functionality.
Input:	Login as admin; access "Manage Users"; search for a user by email; filter by role.
Expected Result:	User list loads; search returns correct results; filter by role displays only matching users; can view user details (email, role, createdAt).
________________________________________
•	Test Case 77
Epic:	E11 — Administrative Control and System Governance
Test ID:	E11US1TC2
User Story ID:	US11.1 – As an admin, I want to view and manage all user accounts.
Test Case:	Verify admin can delete user accounts.
Input:	Select a user and tap "Delete Account".
Expected Result:	User removed from Firestore users collection; Firebase Auth account deleted; user can no longer login; deletion logged.
________________________________________
•	Test Case 78
Epic:	E11 — Administrative Control and System Governance
Test ID:	E11US2TC1
User Story ID:	US11.2 – As an admin, I want to view and manage all registered NGOs.
Test Case:	Verify admin can view all NGOs and their registration dates.
Input:	Login as admin; access "Manage NGOs"; observe NGO list with names and registration dates.
Expected Result:	All active NGOs displayed; details include name, contact email, registration date, and status (active/inactive).
________________________________________
•	Test Case 79
Epic:	E11 — Administrative Control and System Governance
Test ID:	E11US2TC2
User Story ID:	US11.2 – As an admin, I want to view and manage all registered NGOs.
Test Case:	Verify admin can remove an NGO from the platform.
Input:	Select an NGO and tap "Remove from Platform".
Expected Result:	NGO removed from Firestore; admin dashboard no longer shows NGO; NGO users receive notification that their organization was removed.
________________________________________
•	Test Case 80
Epic:	E11 — Administrative Control and System Governance
Test ID:	E11US3TC1
User Story ID:	US11.3 – As an admin, I want to review pending NGO registration requests.
Test Case:	Verify admin can approve NGO registration requests.
Input:	Login as admin; access "Manage NGO Requests"; see pending request; review request details; tap "Approve".
Expected Result:	Request status updated to "approved"; NGO user receives confirmation; request moved to approved section; decision logged with timestamp and admin name.
________________________________________
•	Test Case 81
Epic:	E11 — Administrative Control and System Governance
Test ID:	E11US3TC2
User Story ID:	US11.3 – As an admin, I want to review pending NGO registration requests.
Test Case:	Verify admin can reject NGO registration requests with reason.
Input:	Access pending request; tap "Reject"; enter rejection reason; confirm.
Expected Result:	Request status updated to "rejected"; rejection reason sent to applicant; request logged; applicant notified and can reapply if desired.
________________________________________
•	Test Case 82
Epic:	E11 — Administrative Control and System Governance
Test ID:	E11US4TC1
User Story ID:	US11.4 – As an admin, I want to review and moderate incidents.
Test Case:	Verify admin can access incident moderation panel and verify/remove incidents.
Input:	Login as admin; access incident moderation; see flagged incidents; mark one as verified; remove one as false.
Expected Result:	Verified incidents remain in feed and visible to all users; removed incidents deleted from Firestore; moderation action logged with reason.