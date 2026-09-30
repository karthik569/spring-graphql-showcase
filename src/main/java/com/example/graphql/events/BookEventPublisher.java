package com.example.graphql.events;

import com.example.graphql.model.Book;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class BookEventPublisher {

    private final Sinks.Many<Book> sink = Sinks.many().multicast().directBestEffort();

    public void publish(Book book) {
        sink.tryEmitNext(book);
    }

    public Flux<Book> stream() {
        return sink.asFlux();
    }
}
