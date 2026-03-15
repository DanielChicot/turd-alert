create table readings (
    company      text not null,
    site_id      text not null,
    polled_at    timestamptz not null,
    status       smallint not null,
    status_start timestamptz,

    primary key (company, site_id, polled_at),
    foreign key (company, site_id) references sites(company, site_id)
) partition by range (polled_at);
