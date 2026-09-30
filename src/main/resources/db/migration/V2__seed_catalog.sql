insert into authors (id, name, country) values
    ('author-1', 'Joshua Bloch', 'USA'),
    ('author-2', 'Martin Fowler', 'UK'),
    ('author-3', 'Robert C. Martin', 'USA');

insert into books (id, title, pages, price, stock, author_id) values
    ('book-1', 'Effective Java', 416, 45.0, 50, 'author-1'),
    ('book-2', 'Refactoring', 448, 55.0, 30, 'author-2'),
    ('book-3', 'Clean Code', 464, 40.0, 75, 'author-3'),
    ('book-4', 'Java Puzzlers', 312, 35.0, 20, 'author-1');

insert into magazines (id, title, issue_number, publisher, published_on, website) values
    ('magazine-1', 'Java Magazine', 42, 'Oracle', timestamp with time zone '2024-05-01 00:00:00+00:00', 'https://javamagazine.example.com'),
    ('magazine-2', 'GraphQL Weekly', 7, 'GraphQL Foundation', timestamp with time zone '2024-06-15 00:00:00+00:00', 'https://graphqlweekly.example.com'),
    ('magazine-3', 'Spring Weekly', 3, 'VMware', timestamp with time zone '2024-07-01 00:00:00+00:00', null);

alter sequence book_seq restart with 5;
alter sequence magazine_seq restart with 4;
