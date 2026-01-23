# Student Gradebook & Team Formation System with Qdrant and Vaadin

This application is a specialized tool for managing student grades and automatically forming project teams based on student performance vectors. It heavily utilizes Qdrant, a vector database, to store and process student data, allowing for advanced analytics like finding similar students or identifying problematic areas in the curriculum.

## Features

-   **Student Management**: Add, edit, and delete students.
-   **Dynamic Gradebook**: Configure courses with custom lectures and laboratories.
-   **Vector-Based Search**: Uses Qdrant to store student data as vectors.
-   **Team Generator**: Automatically forms pairs of students for projects based on complementary skills or performance.
-   **Problem Area Detection**: Identifies which topics (lectures/labs) caused the most trouble for the class.
-   **Configuration Import**: Easily setups a new course by importing a JSON configuration file.

## Prerequisites

-   **Java 17** or higher
-   **Docker** (for running Qdrant)
-   **Maven** (optional, wrapper included)

## Getting Started

### 1. Start Qdrant

The application requires a running Qdrant instance. You can start it using Docker:

```bash
docker run -p 6333:6333 -p 6334:6334 \
    -v $(pwd)/qdrant_storage:/qdrant/storage \
    qdrant/qdrant
```

### 2. Run the Application

You can run the application directly using the Maven wrapper:

```bash
./mvnw spring-boot:run
```

Once started, open your browser and navigate to: `http://localhost:8080`

## Initial Setup & Configuration

On the first launch, you will be redirected to the **Setup Wizard**. You have two options:

1.  **Manual Configuration**: Enter the course name and add classes (lectures/labs) one by one through the UI.
2.  **JSON Import**: Upload a JSON file to instantly configure the course.

### JSON Configuration Format

To make manual creation easy, you **do not** need to provide UUIDs for the classes. The system will automatically generate them for you upon import.

**Example `course_config.json`:**

```json
{
  "courseName": "Advanced Java Programming",
  "classes": [
    {
      "topic": "Introduction to Streams",
      "date": "2023-10-01",
      "type": "LECTURE"
    },
    {
      "topic": "Stream API Practice",
      "date": "2023-10-02",
      "type": "LAB"
    },
    {
      "topic": "Spring Boot Basics",
      "date": "2023-10-15",
      "type": "LECTURE"
    },
    {
      "topic": "Building REST API",
      "date": "2023-10-16",
      "type": "LAB"
    }
  ]
}
```

### Field Descriptions:

-   **`courseName`**: (String) The name of the subject/course.
-   **`classes`**: (Array) A list of class sessions.
    -   **`topic`**: (String) The topic or title of the class.
    -   **`date`**: (String) Date in `YYYY-MM-DD` format.
    -   **`type`**: (String) The type of class. Must be either `LECTURE` or `LAB`.

## Usage

1.  **Gradebook**: After setup, you will see the main gradebook view. You can add students and grade them on the configured classes.
2.  **Teams**: Navigate to the "Teams" tab to generate student pairs.
3.  **Analytics**: View the "Weakest Area" stats in the Teams view to see where students are struggling.
