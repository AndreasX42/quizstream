# QuizStream

# QuizStream: Create and solve quizzes of YouTube videos

**QuizStream** is aimed at making learning more engaging by turning YouTube videos into interactive quizzes. Whether you're a teacher, a content creator or just someone who loves to learn, QuizStream simplifies the process of creating quizzes based on video content. All you need is a YouTube video that you'd like to turn into a quiz. The application does the heavy lifting for you, automatically generating questions from the video's content. You can customize the quiz additionally by setting the difficulty level and by choosing the language.

## 📖 Stack

`Frontend` [Angular 18](https://angular.dev/) - ([GitHub Repo](https://github.com/AndreasX42/quizstream-angular)) \
`Backend` [Java 21](https://openjdk.org/) [Python](https://www.python.org/)\
`LLM Frameworks` [LangChain](https://www.langchain.com/) [OpenAI](https://www.openai.com/) \
`API Frameworks` [Spring Boot 3](https://spring.io/projects/spring-boot) [FastAPI](https://fastapi.tiangolo.com/)\
`DBs` [Amazon RDS for PostgreSQL](https://aws.amazon.com/rds/postgresql/) [PGVector](https://github.com/pgvector/pgvector)\
`CI/CD` [AWS CodePipeline](https://aws.amazon.com/codepipeline/) [AWS CodeBuild](https://aws.amazon.com/codebuild/) 

## 🌐 Architecture

The app is built on AWS with the following architecture:

![aws_diagram](https://github.com/user-attachments/assets/62eb52b8-2ad1-4826-8f84-63686c5b1567)


## 🌟 Features

- **Easy-to-Use Interface**: Our sleek Angular-based frontend makes navigating and using QuizStream a breeze.
- **Powerful Backend**: We use Spring Boot to handle all the data and requests behind the scenes.
- **Smart AI Processing**: Our Python-based backend converts video content into quiz questions effortlessly.
- **Reliable Data Storage**: Your quizzes and user information are safely stored in a PostgreSQL database.

## 🛠️ Development

Directory Structure

- `/springboot_api` Spring Boot API
- `/quiz_generator` Python backend for quiz generation
- `/postgres` Database for development
