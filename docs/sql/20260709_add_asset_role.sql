alter table assets
    add column if not exists asset_role varchar(30);

update assets
set asset_role = case
    when type = 'GENERATED' then 'GENERATED_RESULT'
    when type = 'IMAGE' then 'PRODUCT_IMAGE'
    else asset_role
end
where asset_role is null;
