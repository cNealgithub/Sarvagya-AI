# Sarvagya AI 🧠

Sarvagya AI is a localized, enterprise-grade Retrieval-Augmented Generation (RAG) chatbot system designed to securely process, query, and retrieve institutional knowledge for Dr. Shyama Prasad Mukherjee University (DSPMU). Built on a reactive, cloud-native architecture, it leverages local LLMs to ensure data privacy while maintaining high-throughput inference.

## 🚀 Key Features

* **Automated ETL Knowledge Pipeline:** A robust Extract, Transform, Load workflow that ingests unstructured data (documents, notices, web pages), applies semantic chunking, generates embeddings, and indexes them for rapid retrieval.
* **Local LLM Inference:** Completely private, local text generation and tool-calling powered by Ollama, eliminating reliance on external APIs.
* **High-Performance Vector Search:** Utilizes PostgreSQL with `pgvector` and HNSW indexing for low-latency, high-dimensional similarity searches.
* **Reactive Architecture:** Built with Spring WebFlux to handle concurrent user requests using non-blocking, asynchronous I/O streams.
* **Agentic Workflows:** Integrates the Model Context Protocol (MCP) to allow the AI to interact with external tools and APIs dynamically.
* **Containerized Deployment:** Fully dockerized ecosystem for seamless orchestration of the database, backend, and LLM inference engine.

## 🛠️ Tech Stack

* **Backend:** Java, Spring Boot, Spring WebFlux, Spring AI
* **Database:** PostgreSQL, pgvector extension
* **AI / LLM:** Ollama (Llama 3 / Mistral), Model Context Protocol (MCP)
* **DevOps & Deployment:** Docker, Docker Compose
* **Build Tool:** Maven

## 🔄 Architecture & ETL Flow

1. **Extract:** Raw institutional documents and text are ingested into the Spring Boot backend.
2. **Transform:** The text is cleaned and passed through a semantic text splitter to create contextually whole chunks. Spring AI then interfaces with a local embedding model to convert these chunks into dense vector representations.
3. **Load:** The vectors and their associated metadata are stored in PostgreSQL (`pgvector`), utilizing an HNSW index to optimize for cosine similarity.
4. **Retrieve & Generate (RAG):** When a user submits a query, it is embedded and matched against the vector database. The most relevant chunks are retrieved and injected into the system prompt for the local LLM (via Ollama) to generate a context-aware, hallucination-free response.

## ⚙️ Local Setup & Installation

### Prerequisites
* Docker & Docker Compose
* Java 21+
* Maven

### Running the System

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/your-username/sarvagya-ai.git](https://github.com/your-username/sarvagya-ai.git)
   cd sarvagya-ai
