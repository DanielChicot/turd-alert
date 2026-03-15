create table sites (
    company     text not null,
    site_id     text not null,
    site_name   text,
    watercourse text,
    latitude    double precision not null,
    longitude   double precision not null,

    primary key (company, site_id)
);

create index idx_sites_location on sites (latitude, longitude);
