# 🏨 Smart Hotel Management System

A modern, secure, and cloud-ready **Hotel Management System** designed to digitize and automate hotel operations including room management, reservations, customer management, billing, reporting, and tourist recommendations.

The system is built using a **React.js frontend**, **Spring Boot REST API backend**, and **PostgreSQL database hosted on Supabase**, with **JWT-based authentication and role-based access control**.

---

## 📌 Overview

The Smart Hotel Management System is a full-stack web application developed to address common challenges in traditional and fragmented hotel management systems.

The application provides two primary user roles:

- 👨‍💼 **Hotel Administrator**
- 👤 **Guest / Customer**

The system provides real-time room availability, prevents double bookings through server-side validation and database transactions, manages the complete booking lifecycle, and provides administrative reporting and management capabilities.

The project was developed as a **Final Year Project for the Bachelor of Technology in Computer Science and Engineering** at **ICFAI Foundation for Higher Education, Hyderabad**.

---

## ✨ Key Features

### 👤 Guest Features

- User registration and login
- JWT-based authentication
- Browse available rooms
- View room details, amenities, pricing, and photographs
- Real-time room availability checking
- Online room reservations
- View personal booking history
- Manage personal bookings
- Automated booking confirmation
- Tourist attraction recommendations
- Responsive interface for desktop, tablet, and mobile devices

### 👨‍💼 Administrator Features

- Secure administrator authentication
- Admin dashboard
- Room creation and management
- Room categorization and pricing
- Room status management
- View and manage all bookings
- Update booking status
- Customer management
- Billing and invoice management
- Tourist attraction management
- Reports and analytics
- Overall hotel operations management

### 🔐 Security

- JWT-based stateless authentication
- Role-Based Access Control (RBAC)
- BCrypt password hashing
- HTTPS-secured communication
- Server-side authorization
- Parameterized database queries through JPA/Hibernate
- Protected administrative APIs

### ⚡ Smart Booking System

One of the major components of the project is the real-time room availability engine.

The system:

1. Accepts the customer's check-in and check-out dates.
2. Checks existing reservations.
3. Identifies rooms with overlapping bookings.
4. Filters unavailable rooms.
5. Displays only rooms that can actually be booked.
6. Performs server-side validation before creating a reservation.
7. Uses database transactions to prevent concurrent double bookings.

---

## 🏗️ System Architecture

The application follows a **three-tier client-server architecture**:

```text
                    ┌─────────────────────────┐
                    │       Client/User       │
                    │   Web Browser / Mobile  │
                    └────────────┬────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │     React.js Frontend   │
                    │                         │
                    │ • User Interface        │
                    │ • Routing               │
                    │ • State Management      │
                    │ • API Communication     │
                    └────────────┬────────────┘
                                 │ REST API
                                 ▼
                    ┌─────────────────────────┐
                    │    Spring Boot Backend  │
                    │                         │
                    │ • REST Controllers      │
                    │ • Business Logic        │
                    │ • Spring Security       │
                    │ • JWT Authentication   │
                    │ • JPA / Hibernate      │
                    └────────────┬────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │ PostgreSQL / Supabase   │
                    │                         │
                    │ • Users                 │
                    │ • Rooms                 │
                    │ • Bookings              │
                    │ • Payments              │
                    │ • Tourist Attractions   │
                    └─────────────────────────┘
```

The separation between frontend, backend, and database allows the individual layers to be maintained and scaled independently.

---

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| Frontend | React.js |
| Backend | Java + Spring Boot |
| API | RESTful API |
| Database | PostgreSQL |
| Cloud Database | Supabase |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security |
| Authentication | JSON Web Token (JWT) |
| Password Hashing | BCrypt |
| Frontend Styling | CSS / Component Libraries |
| Backend Deployment | Render |
| Frontend Deployment | Vercel |
| Version Control | Git / GitHub |

The project specifically uses PostgreSQL through Supabase as its cloud-based relational database.

---

## 📂 Main Modules

### 1. Authentication Module

Handles:

- Registration
- Login
- JWT generation
- JWT validation
- Role-based authorization
- Password security

### 2. Room Management Module

Handles:

- Room creation
- Room categorization
- Room pricing
- Capacity
- Amenities
- Room images
- Room status
- Availability

### 3. Booking Management Module

Handles:

- Room reservation
- Availability checking
- Booking confirmation
- Booking status
- Check-in
- Check-out
- Booking history
- Double-booking prevention

### 4. Billing Module

Handles:

- Booking charges
- Invoice generation
- Payment records
- Billing information

### 5. Tourist Suggestion Module

Provides curated recommendations for:

- Tourist attractions
- Restaurants
- Cultural locations
- Entertainment options

### 6. Reporting Module

Provides administrators with information useful for monitoring hotel operations and booking activity.

---

## 👥 User Roles

### Administrator

The administrator has access to the complete hotel management functionality, including:

```text
Room Management
       ↓
Booking Management
       ↓
Customer Management
       ↓
Billing & Invoices
       ↓
Tourist Attractions
       ↓
Reports & Dashboard
```

### Guest

Guests have access to self-service functionality:

```text
Register / Login
       ↓
Browse Rooms
       ↓
Check Availability
       ↓
Book Room
       ↓
View Booking
       ↓
Check Booking History
       ↓
Explore Tourist Attractions
```

Guests cannot access administrative functionality or other customers' data.

---

## 🗄️ Database

The system uses **PostgreSQL** as its relational database, hosted through **Supabase**.

The major database entities include:

- `users`
- `rooms`
- `bookings`
- `payments`

The database design follows relational database principles and is structured to satisfy **1NF, 2NF, and 3NF** requirements.

### Example Database Structure

```text
Users
 ├── User ID
 ├── Name
 ├── Email
 ├── Password Hash
 ├── Role
 ├── Phone
 └── Account Status

Rooms
 ├── Room ID
 ├── Room Number
 ├── Room Type
 ├── Price
 ├── Capacity
 ├── Description
 ├── Amenities
 ├── Status
 ├── Image URL
 └── Floor Number

Bookings
 ├── Booking ID
 ├── User
 ├── Room
 ├── Check-in Date
 ├── Check-out Date
 ├── Booking Status
 └── Booking Details

Payments
 ├── Payment ID
 ├── Booking
 ├── Amount
 ├── Payment Status
 ├── Transaction ID
 └── Payment Date
```

---

## 🔌 REST API

The backend exposes RESTful endpoints for the major system operations.

Examples include:

```text
POST   /api/auth/login
POST   /api/auth/register

GET    /api/rooms
GET    /api/rooms/{id}

POST   /api/bookings
GET    /api/bookings
GET    /api/bookings/{id}

GET    /api/admin/bookings
PUT    /api/admin/bookings/{id}/status

GET    /api/admin/dashboard

GET    /api/tourist
POST   /api/tourist
```

Authentication-protected endpoints use JWT-based authorization, with administrative endpoints restricted to authorized administrators.

---

## 🔐 Authentication Flow

The application uses **JSON Web Tokens (JWT)** for stateless authentication.

```text
User
  │
  ▼
Login
  │
  ▼
Spring Boot Authentication
  │
  ▼
Validate Credentials
  │
  ▼
Generate JWT
  │
  ▼
Return Token
  │
  ▼
Client Stores Token
  │
  ▼
Token Sent With API Requests
  │
  ▼
JWT Validation
  │
  ▼
Role-Based Authorization
```

JWT claims contain information such as the user identity, email, and role.

---

## 🚀 Getting Started

### Prerequisites

Install the following software before running the project locally:

- **JDK 17 or higher**
- **Apache Maven 3.8+**
- **Node.js 18+**
- **npm 9+**
- **Git**
- **PostgreSQL 14+** or a **Supabase account**

These are the prerequisites specified in the project deployment documentation.

---

## 📥 Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
cd YOUR_REPOSITORY
```

> Replace `YOUR_USERNAME` and `YOUR_REPOSITORY` with your GitHub repository details.

---

# ⚙️ Backend Setup

Navigate to the backend directory:

```bash
cd backend
```

Configure your database and security environment variables.

Example:

```env
SUPABASE_DB_PASSWORD=your_database_password
JWT_SECRET=your_jwt_secret_key
```

Build the Spring Boot application:

```bash
mvn clean install
```

Run the backend:

```bash
mvn spring-boot:run
```

The backend API will be available at:

```text
http://localhost:8080
```

---

# 💻 Frontend Setup

Navigate to the frontend directory:

```bash
cd frontend
```

Install dependencies:

```bash
npm install
```

Create a `.env` file:

```env
REACT_APP_API_BASE_URL=http://localhost:8080/api
```

Start the React development server:

```bash
npm start
```

The frontend will then be available through the development server.

---

## 🌐 Deployment

The project architecture supports cloud deployment.

### Backend

The Spring Boot backend can be deployed using:

**Render**

### Frontend

The React.js frontend can be deployed using:

**Vercel**

### Database

The PostgreSQL database is hosted using:

**Supabase**

The project report specifies Render for backend deployment, Vercel for frontend deployment, and Supabase for the managed PostgreSQL database.

---

## 📊 Testing

The system was tested using multiple testing approaches including:

- Unit Testing
- Integration Testing
- System Testing
- User Acceptance Testing
- Performance Testing
- Security Testing

According to the project report, the system achieved a **100% pass rate across 25 functional test cases** and passed the specified non-functional performance and security benchmarks.

The report also records standard API response times below **500 ms** and complex reporting queries below **1 second** under the tested conditions.

---

## 📈 Project Highlights

- ✅ Real-time room availability
- ✅ Double-booking prevention
- ✅ JWT authentication
- ✅ Role-Based Access Control
- ✅ Secure password hashing
- ✅ RESTful API architecture
- ✅ Cloud-hosted PostgreSQL database
- ✅ Responsive web interface
- ✅ Automated billing and invoice generation
- ✅ Tourist recommendation system
- ✅ Admin dashboard
- ✅ Reporting functionality
- ✅ Three-tier architecture
- ✅ Cloud deployment support

---

## ⚠️ Current Limitations

The current version has some limitations:

- No integrated online payment gateway
- No OTA integration such as Booking.com or Expedia
- Supports a single hotel/property
- Tourist recommendations are manually curated
- No native Android/iOS application
- Housekeeping and maintenance management are not currently included

These limitations are documented as part of the project's current scope.

---

## 🔮 Future Enhancements

Planned improvements include:

### 💳 Payment Gateway Integration

Integrate online payment providers to enable direct payment processing during booking.

### 🏨 Multi-Property Support

Extend the architecture to support hotel chains and multiple properties.

### 🤖 AI-Powered Recommendations

Introduce AI-based recommendations for:

- Rooms
- Tourist attractions
- Restaurants
- Personalized experiences

### 💰 Dynamic Pricing

Implement intelligent pricing based on:

- Demand
- Occupancy
- Season
- Booking patterns

### 📱 Mobile Applications

Develop native Android and iOS applications.

### 🌐 OTA Integration

Integrate external booking platforms and channel managers.

### 🧹 Housekeeping & Maintenance

Add dedicated modules for:

- Housekeeping tasks
- Room cleaning status
- Maintenance requests
- Staff assignment

### 📊 Advanced Business Intelligence

Introduce advanced analytics and dashboards for:

- Revenue analysis
- Occupancy trends
- Customer behavior
- Booking patterns
- Business forecasting

These enhancements are part of the project's documented future roadmap.

---

## 🎯 Project Objectives

The project was designed to:

1. Digitize and centralize core hotel operations.
2. Provide separate functionality for administrators and guests.
3. Implement real-time room availability.
4. Prevent double bookings.
5. Provide secure JWT authentication.
6. Develop a scalable REST API.
7. Use a cloud-based PostgreSQL database.
8. Provide tourist recommendations.
9. Support cloud deployment and accessibility.

---

## 🧩 Project Structure

A recommended repository structure for the application is:

```text
smart-hotel-management-system/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   ├── pom.xml
│   └── README.md
│
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   ├── context/
│   │   └── hooks/
│   ├── package.json
│   └── README.md
│
├── database/
│   └── schema.sql
│
├── docs/
│   └── project-report.pdf
│
├── .gitignore
└── README.md
```

> Adjust the structure above to match the actual folders in your repository.

---

## 🔒 Environment Variables

Never commit passwords, JWT secrets, database credentials, API keys, or other sensitive configuration to GitHub.

Example backend configuration:

```env
SUPABASE_DB_PASSWORD=your_database_password
JWT_SECRET=your_jwt_secret
```

Example frontend configuration:

```env
REACT_APP_API_BASE_URL=http://localhost:8080/api
```

Add environment files to `.gitignore`:

```gitignore
.env
.env.local
.env.production
application-local.properties
```

---

## 📷 Screenshots

Add screenshots of the application here to make the repository more attractive and easier to understand.

Suggested screenshots:

```text
docs/screenshots/
├── home-page.png
├── login-page.png
├── room-listing.png
├── room-details.png
├── booking-page.png
├── customer-dashboard.png
├── admin-dashboard.png
├── booking-management.png
├── billing.png
└── tourist-suggestions.png
```

Then display them in this section:

```markdown
![Home Page](docs/screenshots/home-page.png)

![Room Listing](docs/screenshots/room-listing.png)

![Admin Dashboard](docs/screenshots/admin-dashboard.png)
```

---

## 📚 Documentation

The complete academic project documentation contains:

- Introduction
- Literature Review
- System Analysis
- Requirements Specification
- System Design
- UML Diagrams
- Database Design
- Implementation
- REST API Design
- Authentication
- Deployment
- Testing
- Results and Discussion
- Advantages
- Limitations
- Future Enhancements
- Conclusion

---

## 🎓 Academic Information

**Project:** Smart Hotel Management System

**Student:** U. Mohan Krishna Reddy

**Degree:** Bachelor of Technology in Computer Science and Engineering

**Institution:** ICFAI Foundation for Higher Education, Hyderabad

**Department:** Department of Computer Science and Engineering

**Project Guide:** Dr. K. Bhargavi, Assistant Professor, CSE

**Academic Year:** 2025–2026

The project report identifies the work as a final-year B.Tech Computer Science and Engineering project.

---

## 📜 License

This project was developed as an academic final-year project.

If you intend to make the source code available for reuse, add an appropriate open-source license such as **MIT License** after confirming that it fits your project and institutional requirements.

---

## 👨‍💻 Author

### Mohan Krishna Reddy

**Computer Science & Engineering**

ICFAI Foundation for Higher Education, Hyderabad

---

## ⭐ Support

If you find this project useful or interesting, consider giving the repository a ⭐ on GitHub.

---

## 📌 Keywords

```text
Hotel Management System
Smart Hotel Management
Hotel Booking System
React.js
Spring Boot
Java
PostgreSQL
Supabase
JWT Authentication
REST API
Spring Security
Hibernate
JPA
Cloud Database
Hotel Reservation
Room Management
Booking Management
Billing System
Tourist Recommendation
Full Stack Development
```

---

### 📄 Project Report

The complete project report is included in the repository for detailed information about the system architecture, requirements, database design, implementation, testing, deployment, and future enhancements.
