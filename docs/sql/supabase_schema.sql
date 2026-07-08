-- Supabase/PostgreSQL schema for Aven Studio MVP.
-- Safe to run on an existing database: this script does not DROP tables.

create table if not exists brands (
    id bigserial primary key,
    name varchar(50) not null unique,
    site_url varchar(100),
    mood_guide_url varchar(100),
    created_at timestamptz not null default now()
);

create table if not exists users (
    id bigserial primary key,
    login_id varchar(50) not null unique,
    name varchar(20),
    password varchar(200) not null,
    created_at timestamptz not null default now(),
    brand_id bigint not null,
    constraint fk_users_brand
        foreign key (brand_id) references brands(id)
);

create table if not exists refresh_tokens (
    id bigserial primary key,
    user_id bigint not null unique,
    token varchar(500) not null,
    expires_at timestamptz not null,
    constraint fk_refresh_tokens_user
        foreign key (user_id) references users(id)
        on delete cascade
);

create table if not exists products (
    id bigserial primary key,
    code varchar(30) not null,
    name varchar(50) not null,
    category varchar(50),
    gender char(1) default 'U',
    color varchar(30),
    price integer not null default 0,
    fit_description varchar(255),
    description text,
    fabrication_description varchar(255),
    created_at timestamptz not null default now(),
    brand_id bigint,
    constraint fk_products_brand
        foreign key (brand_id) references brands(id)
);

create table if not exists assets (
    id bigserial primary key,
    type varchar(30) not null,
    file_url varchar(500) not null,
    object_name varchar(500) not null,
    original_filename varchar(255),
    content_type varchar(100),
    size bigint,
    prompt text,
    description text,
    status varchar(20),
    created_at timestamptz not null default now(),
    product_id bigint,
    constraint fk_assets_product
        foreign key (product_id) references products(id)
        on delete cascade
);

create table if not exists sizes (
    id bigserial primary key,
    size varchar(30) not null,
    product_id bigint not null,
    constraint fk_sizes_product
        foreign key (product_id) references products(id)
        on delete cascade
);

create table if not exists size_measurements (
    id bigserial primary key,
    name varchar(50) not null,
    value numeric(5, 2) not null,
    unit varchar(20) not null default 'cm',
    size_id bigint not null,
    constraint fk_size_measurements_size
        foreign key (size_id) references sizes(id)
        on delete cascade
);

create table if not exists materials (
    id bigserial primary key,
    name varchar(50) not null,
    percent numeric(5, 2) default 0,
    product_id bigint not null,
    constraint fk_materials_product
        foreign key (product_id) references products(id)
        on delete cascade
);

create table if not exists sheet_connections (
    id bigserial primary key,
    url varchar(200) not null,
    last_synced_at timestamptz,
    sync_status varchar(20) default 'SUCCESS',
    created_at timestamptz not null default now(),
    brand_id bigint not null,
    constraint fk_sheet_connections_brand
        foreign key (brand_id) references brands(id)
        on delete cascade
);

create table if not exists detail_pages (
    id bigserial primary key,
    status varchar(20) default 'EXPORTED',
    canvas_data text,
    html_content text,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    product_id bigint not null,
    constraint fk_detail_pages_product
        foreign key (product_id) references products(id)
        on delete cascade
);

create table if not exists copywritings (
    id bigserial primary key,
    block_order integer not null,
    template_key varchar(50) not null,
    slot_key varchar(50) not null,
    content text,
    created_at timestamptz not null default now(),
    detail_page_id bigint,
    constraint fk_copywritings_detail_page
        foreign key (detail_page_id) references detail_pages(id)
        on delete cascade
);

create table if not exists copy_reviews (
    id bigserial primary key,
    risk_type varchar(30) not null,
    risk_level varchar(20) not null,
    original_text text not null,
    reason text,
    suggestion text,
    created_at timestamptz not null default now(),
    copywriting_id bigint not null,
    constraint fk_copy_reviews_copywriting
        foreign key (copywriting_id) references copywritings(id)
        on delete cascade
);

create table if not exists brand_moods (
    id bigserial primary key,
    user_id bigint not null,
    brand_id bigint not null,
    name varchar(100) not null,
    version integer not null default 1,
    mood_type varchar(30) not null default 'BASE',
    status varchar(20) not null default 'ACTIVE',
    source_type varchar(30),
    source_url varchar(500),
    brand_summary text,
    design_philosophy text,
    tone_keywords jsonb not null default '[]'::jsonb,
    mood_attributes jsonb not null default '{}'::jsonb,
    color_palette jsonb not null default '[]'::jsonb,
    negative_keywords jsonb not null default '[]'::jsonb,
    brand_instruction text not null default '',
    custom_brand_note text,
    raw_analysis_json jsonb not null default '{}'::jsonb,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    constraint fk_brand_moods_user
        foreign key (user_id) references users(id)
        on delete cascade,
    constraint fk_brand_moods_brand
        foreign key (brand_id) references brands(id)
        on delete cascade,
    constraint uq_brand_moods_brand_version
        unique (brand_id, version)
);

create table if not exists shot_templates (
    id bigserial primary key,
    cut_type varchar(30) not null,
    template_key varchar(50) not null unique,
    name varchar(100) not null,
    prompt_text text not null,
    negative_prompt text,
    reference_asset_type varchar(30),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz
);

create table if not exists reference_assets (
    id bigserial primary key,
    reference_type varchar(30) not null,
    reference_key varchar(100) not null unique,
    name varchar(100) not null,
    image_url varchar(500) not null,
    object_name varchar(500),
    content_type varchar(100),
    description text,
    is_active boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamptz not null default now()
);

create table if not exists image_generation_jobs (
    id bigserial primary key,
    brand_id bigint not null,
    product_id bigint not null,
    brand_mood_id bigint not null,
    user_id bigint not null,
    cut_type varchar(30) not null,
    generation_mode varchar(30) not null,
    prompt text not null,
    negative_prompt text,
    user_options_json jsonb not null default '{}'::jsonb,
    reference_json jsonb not null default '{}'::jsonb,
    ai_name varchar(100),
    status varchar(30) not null default 'PENDING',
    requested_count integer not null default 1,
    output_count integer not null default 0,
    latency_ms integer,
    error_message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz,
    constraint fk_image_generation_jobs_brand
        foreign key (brand_id) references brands(id),
    constraint fk_image_generation_jobs_product
        foreign key (product_id) references products(id),
    constraint fk_image_generation_jobs_brand_mood
        foreign key (brand_mood_id) references brand_moods(id),
    constraint fk_image_generation_jobs_user
        foreign key (user_id) references users(id)
);

create table if not exists image_generation_results (
    id bigserial primary key,
    job_id bigint not null,
    asset_id bigint,
    shot_template_id bigint,
    output_index integer not null default 0,
    product_view varchar(50),
    status varchar(30) not null default 'GENERATED',
    score numeric(5, 2),
    created_at timestamptz not null default now(),
    constraint fk_image_generation_results_job
        foreign key (job_id) references image_generation_jobs(id)
        on delete cascade,
    constraint fk_image_generation_results_asset
        foreign key (asset_id) references assets(id),
    constraint fk_image_generation_results_shot_template
        foreign key (shot_template_id) references shot_templates(id)
);

create index if not exists idx_products_brand_id on products(brand_id);
create index if not exists idx_assets_product_id on assets(product_id);
create index if not exists idx_brand_moods_brand_id on brand_moods(brand_id);
create index if not exists idx_image_generation_jobs_brand_id on image_generation_jobs(brand_id);
create index if not exists idx_image_generation_jobs_product_id on image_generation_jobs(product_id);
create index if not exists idx_image_generation_results_job_id on image_generation_results(job_id);

insert into shot_templates (cut_type, template_key, name, prompt_text, negative_prompt, reference_asset_type)
values
    ('PRODUCT_CUT', 'FRONT', '정면', '상품의 정면 전체가 명확하게 보이는 제품컷으로 생성한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'BACK', '후면', '상품의 후면 전체가 명확하게 보이는 제품컷으로 생성한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'SIDE', '측면', '상품의 측면 구조와 두께감이 보이는 제품컷으로 생성한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'THREE_QUARTER', '45도', '상품을 45도 사선 각도에서 입체감 있게 보여주는 제품컷으로 생성한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'FLAT_LAY', '플랫레이', '상품을 바닥에 평평하게 놓은 플랫레이 제품컷으로 생성한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'HANGING', '행잉', '상품을 옷걸이에 건 상태의 행잉 제품컷으로 생성한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'TOP_VIEW', '탑뷰', '상품을 위에서 내려다본 탑뷰 제품컷으로 생성한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'UNDER_BRIM', '챙 안쪽', '모자 챙 안쪽과 하단 구조가 보이는 제품 디테일컷으로 생성한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'DETAIL', '디테일', '상품의 핵심 디테일 영역을 확대해서 보여주는 제품 디테일컷으로 생성한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'LOGO', '로고', '상품의 로고 위치와 자수 또는 프린트 질감이 보이도록 확대한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'FABRIC', '소재', '상품 원단의 조직감, 두께감, 표면 질감이 보이도록 확대한다.', null, 'SHOT_REFERENCE'),
    ('PRODUCT_CUT', 'CONSTRUCTION', '봉제', '봉제선, 시접, 마감 구조가 보이도록 확대한다.', null, 'SHOT_REFERENCE'),
    ('MODEL_CUT', 'FRONT_STANDING', '정면', '모델이 정면을 바라보는 자연스러운 착용컷으로 생성한다.', null, 'POSE'),
    ('MODEL_CUT', 'SIDE_STANDING', '측면', '모델의 측면 실루엣과 상품 핏이 보이는 착용컷으로 생성한다.', null, 'POSE'),
    ('MODEL_CUT', 'BACK_STANDING', '후면', '모델의 후면 실루엣과 상품 뒷면이 보이는 착용컷으로 생성한다.', null, 'POSE'),
    ('MODEL_CUT', 'WALKING', '워킹', '모델이 자연스럽게 걷는 활동감 있는 착용컷으로 생성한다.', null, 'POSE'),
    ('MODEL_CUT', 'SITTING', '앉기', '모델이 앉아 있는 자세의 착용컷으로 생성한다.', null, 'POSE')
on conflict (template_key) do update
set
    cut_type = excluded.cut_type,
    name = excluded.name,
    prompt_text = excluded.prompt_text,
    negative_prompt = excluded.negative_prompt,
    reference_asset_type = excluded.reference_asset_type,
    is_active = true,
    updated_at = now();
