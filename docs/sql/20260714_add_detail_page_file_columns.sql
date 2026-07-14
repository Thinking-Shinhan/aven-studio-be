alter table detail_pages
    drop column if exists canvas_data,
    drop column if exists html_content;

alter table detail_pages
    add column if not exists file_url varchar(500),
    add column if not exists object_name varchar(500),
    add column if not exists original_filename varchar(500),
    add column if not exists size bigint;

alter table detail_pages
    add constraint uq_detail_pages_product_id unique (product_id);
