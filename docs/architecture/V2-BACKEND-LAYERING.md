# V2 Backend layering

For all new/refactored modules:

controller -> service -> repository -> entity

REST contracts live under API/DTO packages. Mapping between persistence entities and response DTOs belongs to MapStruct mapper components, never static `Response.from(entity)` helpers.

Foundation 02 migrates Case, Property, Owner and Document response mapping without changing REST URLs or JSON field names.

Next migration targets: Identity, Agents, Geo and integrations.
