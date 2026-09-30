create table authors (
    id varchar(64) primary key,
    name varchar(255) not null,
    country varchar(64) not null
);

create table books (
    id varchar(64) primary key,
    title varchar(255) not null,
    pages int not null,
    price double precision not null,
    stock int not null,
    author_id varchar(64) not null,
    constraint fk_books_author foreign key (author_id) references authors (id)
);

create table magazines (
    id varchar(64) primary key,
    title varchar(255) not null,
    issue_number int not null,
    publisher varchar(255) not null,
    published_on timestamp with time zone not null,
    website varchar(512)
);

create sequence book_seq start with 1 increment by 1;

create sequence magazine_seq start with 1 increment by 1;
