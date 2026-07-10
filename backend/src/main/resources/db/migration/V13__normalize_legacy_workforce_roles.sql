update nexus_workforce_allocation
set workforce_role = case workforce_role
    when 'PRODUCTION' then 'PRODUCTION_OPERATOR'
    when 'QUALITY' then 'QUALITY_INSPECTOR'
    when 'MAINTENANCE' then 'MAINTENANCE_ENGINEER'
    when 'MATERIAL' then 'MATERIAL_HANDLER'
    when 'MANAGER' then 'FACTORY_MANAGER'
    when 'FACTORY' then 'FACTORY_MANAGER'
    else workforce_role
end
where workforce_role in ('PRODUCTION', 'QUALITY', 'MAINTENANCE', 'MATERIAL', 'MANAGER', 'FACTORY');

update nexus_workforce_allocation
set role_type = case role_type
    when 'PRODUCTION' then 'PRODUCTION_OPERATOR'
    when 'QUALITY' then 'QUALITY_INSPECTOR'
    when 'MAINTENANCE' then 'MAINTENANCE_ENGINEER'
    when 'MATERIAL' then 'MATERIAL_HANDLER'
    when 'MANAGER' then 'FACTORY_MANAGER'
    when 'FACTORY' then 'FACTORY_MANAGER'
    else role_type
end
where role_type in ('PRODUCTION', 'QUALITY', 'MAINTENANCE', 'MATERIAL', 'MANAGER', 'FACTORY');
