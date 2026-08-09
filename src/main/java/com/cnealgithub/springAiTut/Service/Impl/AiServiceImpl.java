package com.cnealgithub.springAiTut.Service.Impl;

import com.cnealgithub.springAiTut.Entity.ResponseStructure;
import com.cnealgithub.springAiTut.Service.AiService;
import com.cnealgithub.springAiTut.Tools.SimpleDateTimeTool;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.VectorStoreRetriever;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final ChatClient ollamaChatClient;
    private final VectorStore vectorStore;
    private final Logger logger = LoggerFactory.getLogger(AiService.class);

    /**
     * Standard Chat with System Prompt Injection
     * * Logic: Uses Spring AI's Fluent API to inject a strict system prompt alongside the user query.
     * Why: The Fluent API (.prompt().user()...) is thread-safe and allows dynamic parameter replacement
     * (like {query}) without messy string concatenation, ensuring the LLM focuses on outputting raw code.
     */
    @Override
    public String chat(String query) {
        String response;

        // Defining the AI's persona and constraints
        String systemPrompt = "You are an expert technical curator for a developer marketplace. "
                + "When a user requests a code template, you must return ONLY the raw code block. "
                + "Do not include greetings, explanations, or markdown formatting outside of the code itself. "
                + "Now reply for this query: {query}";
        try {
            response = ollamaChatClient
                    .prompt()
                    // Prompt parsing: Guides the LLM using the system prompt and safely injects the user query
                    .user(u -> u.text(systemPrompt).param("query", query))
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput()
                    .getText();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return response;
    }

    /**
     * Structured Output Mapping
     * * Logic: Forces the LLM to reply in strict JSON and maps it directly to a Java POJO.
     * Why: The .entity() method automatically handles the OutputConverter logic under the hood.
     * It parses the LLM's string response and serializes it into the ResponseStructure class,
     * avoiding manual JSON parsing errors.
     */
    @Override
    public ResponseStructure structuredChatResponse(String query) {
        String systemPrompt = "You are a structured answering assistant. \n"
                + "Always respond ONLY in valid JSON. \n"
                + "Do not include any text outside of JSON. \n"
                + "Use exactly these keys: \"question\" and \"answer\". \n"
                + "Ensure the JSON is properly formatted with matching braces and quotes.\n";

        ResponseStructure response = ollamaChatClient
                .prompt(query)
                .system(systemPrompt)
                .call()
                .entity(ResponseStructure.class); // Maps JSON directly to entity attributes

        System.out.println(response);
        return response;
    }

    /**
     * Advanced Prompt Templating
     * * Logic: Isolates the prompt structure from the dynamic variables using PromptTemplate.
     * Why: Separating the raw template from the injected Map variables prevents prompt injection attacks
     * and makes the templates highly reusable across different domains (e.g., changing subjectMatter dynamically).
     */
    @Override
    public String chatTemplate(String uQuery, String subjectMatter) {
        // Render User Prompt
        String rawTemplate = "what is {uQuery} , answer in points.";
        PromptTemplate promptTemplate = PromptTemplate.builder().template(rawTemplate).build();
        String renderedMessage = promptTemplate.render(Map.of("uQuery", uQuery));

        // Render System Role Prompt
        SystemPromptTemplate systemPromptTemplate = SystemPromptTemplate.builder()
                .template("you are an expert in {subjectMatter} and always give real life examples ")
                .build();
        String systemRolePrompt = systemPromptTemplate.render(Map.of("subjectMatter", subjectMatter));

        Prompt prompt = new Prompt(renderedMessage);

        return ollamaChatClient
                .prompt(prompt)
                .system(systemRolePrompt)
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();
    }

    /**
     * Contextual Chat with Memory Advisor
     * * Logic: Uses Spring AI Advisors to automatically manage chat history.
     * Why: Instead of manually fetching and appending previous messages to the prompt, the Advisor intercepts
     * the request, retrieves the history associated with the conversationId, and handles the memory context behind the scenes.
     */
    @Override
    public String memoryChat(String query, String conversationId) {
        String conversationIdKey = ChatMemory.CONVERSATION_ID;

        return this.ollamaChatClient
                .prompt(query)
                .tools(new SimpleDateTimeTool())
                .advisors(advisorSpec -> advisorSpec.param(conversationIdKey, conversationId))
                .call()
                .content();
    }

    /**
     * Retrieval-Augmented Generation (RAG) with Reactive Streaming
     * * Logic: Fetches context from a Vector DB, appends it to the system prompt, and streams the response via Flux.
     * Why:
     * 1. Vector Store: similaritySearch isolates relevant data so the LLM doesn't hallucinate.
     * 2. Streaming (Flux): Improves perceived performance on the frontend for long responses.
     * 3. contextWrite: Reactor's Flux loses thread-local context (like conversationId) across async boundaries.
     * Writing it into the reactive context ensures the memory advisor tracks the stream correctly.
     */
    @Override
    public Flux<String> streamingChatResponse(String uQuery, String sm, String conversationId) {

        // 1. Prepare User Template
        String rawUserQuery = "{uQuery} , answer in points";
        PromptTemplate promptTemplate = PromptTemplate.builder().template(rawUserQuery).build();
        String renderedMessage = promptTemplate.render(Map.of("uQuery", uQuery));

        // 2. Vector DB Similarity Search (RAG Context Loading)
//        SearchRequest searchRequest = SearchRequest.builder()
//                .query(uQuery)
//                .topK(3)                      // Fetch top 3 most relevant documents
//                .similarityThreshold(0.5)     // Ignore weak matches below 50% similarity
//                .build();
//
//        List<Document> documentList = vectorStore.similaritySearch(searchRequest);
//        List<String> stringifiedDocumentList = documentList.stream().map(Document::getText).toList();
//        String contextDocument = String.join(",", stringifiedDocumentList);

//        this.logger.info("Context document data: {}", contextDocument);

        // 3. Prepare System Prompt with Context Grounding
//        String rawSystemMessage = "you are the support assistant of BrewBuy and you will clarify the user queries strictly on the basis of DOCUMENTS "
//                + ", and strictly answer to only those questions that are from DOCUMENTS and for any query outside DOCUMENTS don't reply to it , instead respond:-'i cannot answer questions out of my data source' "
//                + "DOCUMENTS:{documents}";

//        SystemPromptTemplate systemPromptTemplate = SystemPromptTemplate.builder().template(rawSystemMessage).build();
//        String renderedSystemMessage = systemPromptTemplate.render(Map.of("documents", contextDocument));

        // 4. Stream Response with Memory Management
        return this.ollamaChatClient
                .prompt()
                .system("you are chat assistant of BrewBuy(or brewbuy)")
                .user(renderedMessage)
                .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", conversationId))
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .stream()
                .content()
                // Explicitly preserve conversationId in the reactive context across asynchronous stream chunks
                .contextWrite(context -> context.put("chat_memory_conversation_id", conversationId));
    }

    /**
     * Vector DB Ingestion
     * * Logic: Converts raw strings into Document entities and persists them to the vector store.
     * Why: LLMs require text to be tokenized and embedded before similarity search can work.
     * The Document wrapper allows Spring AI to pass the text to an embedding model before saving it to the DB.
     */
    @Override
    public void addDataToVectorDb(List<String> dataForVector) {
        List<Document> dataDoc = dataForVector.stream().map(Document::new).toList();
        this.vectorStore.add(dataDoc);
    }

    @Override
    public Flux<String> naiveAdvanceRagChat(String query, String chatId) {

        //build User quer
        String rawQuery = "{userQuery}, answer in points";
        PromptTemplate userPromptTemplate = PromptTemplate.builder().template(rawQuery).build();
        String renderedUserPrompt = userPromptTemplate.render(Map.of("userQuery", query));

//      system prompt
        String systemPrompt = "You are the chat assistant of BrewBuy. "
                + "Strictly answer to only those questions that are from the provided context documents. "
                + "If the answer is not in the context, respond EXACTLY: 'I cannot answer questions out of my data source'.";

//      Building the Naive Rag flow using RetrievalAugmentationAdvisor
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor
                .builder()
                .documentRetriever(VectorStoreDocumentRetriever
                        .builder()
                        .topK(3)
                        .similarityThreshold(0.5)
                        .vectorStore(this.vectorStore)
                        .build())
                .build();
        return this.ollamaChatClient
                .prompt()
                .system(systemPrompt)
                .user(renderedUserPrompt)
//                .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", chatId))
                .advisors(retrievalAugmentationAdvisor)   // Activates Advanced RAG
                .stream()
                .content();
//                .contextWrite(context -> context.put("chat_memory_conversation_id", chatId));
    }

    /**
     * @param query
     * @param chatId
     * @return
     * The advance flow: pre Retrieval -> retrieval -> post retrieval -> generation -> response
     */
    @Override
    public Flux<String> fullyAdvanceRag(String query, String chatId) {
//        user prompt parsing
//        String rawQuery = "{uQuery}, answer in points";
//        PromptTemplate promptTemplate = PromptTemplate.builder().template(rawQuery).build();
//        String renderedQuery = promptTemplate.render(Map.of("uQuery", query));

        //      system prompt
        String systemPrompt = "Strictly answer to only those questions that are from the provided context documents. "
                + "If the answer is not in the context, respond EXACTLY: 'I cannot answer questions out of my data source'.";

//        Pre-Retrieval
        var retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor
                .builder()
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(this.ollamaChatClient.mutate()
                                // Pass the conversation ID to the background transformer client
                                .defaultAdvisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId)))
                        .build())
                // Retrieval
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .similarityThreshold(0.4)
                        .vectorStore(this.vectorStore)
                        .topK(3)
                        .build())
                //Retrieval :: augment query with context (previous queries or chat history)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();
        return this.ollamaChatClient
                .prompt()
                .system(systemPrompt)
                .user(query + ", answer in points")
                .advisors(retrievalAugmentationAdvisor)
                .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", chatId))
                .stream().content()
                .contextWrite(context -> context.put("chat_memory_conversation_id", chatId));
    }
}
