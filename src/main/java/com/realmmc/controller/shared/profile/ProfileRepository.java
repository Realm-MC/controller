package com.realmmc.controller.shared.profile;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.realmmc.controller.shared.role.PlayerRole;
import com.realmmc.controller.shared.storage.mongodb.AbstractMongoRepository;
import com.realmmc.controller.shared.storage.mongodb.MongoRepository;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level; // Importar Level
import java.util.logging.Logger; // Importar Logger

public class ProfileRepository extends AbstractMongoRepository<Profile> {

    private static final Logger LOGGER = Logger.getLogger(ProfileRepository.class.getName()); // Logger

    public ProfileRepository() {
        super(Profile.class, "profiles");
        ensureIndexes();
    }

    private void ensureIndexes() {
        try {
            MongoCollection<Profile> col = collection();
            col.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));
            col.createIndex(Indexes.ascending("username"), new IndexOptions()
                    .unique(true)
                    .collation(Collation.builder()
                            .locale("en")
                            .collationStrength(CollationStrength.SECONDARY) // Case-insensitive
                            .build()));
            col.createIndex(Indexes.descending("lastLogin"));

            // <<< CORREÇÃO: Mudar de hashed para ascending (Multikey Index) >>>
            col.createIndex(Indexes.ascending("roles.roleName"));
            // <<< FIM CORREÇÃO >>>

            col.createIndex(Indexes.ascending("roles.status"));

            LOGGER.info("Índices da coleção 'profiles' verificados/criados com sucesso.");
        } catch (MongoException e) {
            // Loga o erro mas não impede a inicialização (pode ser erro de permissão ou já existe)
            LOGGER.log(Level.SEVERE, "Falha ao criar/verificar índices para a coleção 'profiles'", e);
            // Relança a exceção para fazer o módulo falhar (como aconteceu nos logs)
            // Isso é BOM, pois impede o servidor de iniciar com configuração de DB errada.
            throw e;
        }
    }

    public Optional<Profile> findByUuid(UUID uuid) {
        return findOne(Filters.eq("uuid", uuid));
    }

    public Optional<Profile> findById(int id) {
        return findOne(MongoRepository.idEquals(id));
    }

    public Optional<Profile> findByName(String name) {
        return findOne(Filters.eq("name", name));
    }

    public Optional<Profile> findByUsername(String username) {
        return findOne(Filters.eq("username", username));
    }

    public void upsert(Profile profile) {
        if (profile.getId() == null) throw new IllegalArgumentException("Profile integer _id cannot be null for upsert");
        replace(MongoRepository.idEquals(profile.getId()), profile);
    }

    public void deleteByUuid(UUID uuid) {
        delete(Filters.eq("uuid", uuid));
    }

    public List<Profile> findByActiveRoleName(String roleName) {
        Bson filter = Filters.elemMatch("roles",
                Filters.and(
                        Filters.eq("roleName", roleName),
                        Filters.eq("status", PlayerRole.Status.ACTIVE.name())
                )
        );
        List<Profile> results = new ArrayList<>();
        FindIterable<Profile> found = collection().find(filter)
                .projection(Projections.include("uuid", "name", "username", "roles"));
        found.forEach(results::add);
        return results;
    }

    public List<UUID> findUuidsWithRoleStatus(String roleName, PlayerRole.Status status) {
        List<UUID> uuids = new ArrayList<>();
        Bson filter = Filters.elemMatch("roles",
                Filters.and(
                        Filters.eq("roleName", roleName),
                        Filters.eq("status", status.name())
                )
        );
        collection().find(filter)
                .projection(Projections.include("uuid"))
                .forEach(profile -> uuids.add(profile.getUuid()));
        return uuids;
    }

    public long updateManyMarkRoleAsRemoved(String roleName) {
        Bson filter = Filters.and(
                Filters.eq("roles.roleName", roleName),
                Filters.eq("roles.status", PlayerRole.Status.ACTIVE.name())
        );

        Bson update = Updates.combine(
                Updates.set("roles.$[elem].status", PlayerRole.Status.REMOVED.name()),
                Updates.set("roles.$[elem].removedAt", System.currentTimeMillis()),
                Updates.set("roles.$[elem].paused", false),
                Updates.set("roles.$[elem].pausedTimeRemaining", null)
        );

        List<Bson> arrayFilters = List.of(
                Filters.and(
                        Filters.eq("elem.roleName", roleName),
                        Filters.eq("elem.status", PlayerRole.Status.ACTIVE.name())
                )
        );

        UpdateOptions options = new UpdateOptions().arrayFilters(arrayFilters);

        UpdateResult result = collection().updateMany(filter, update, options);
        return result.getModifiedCount();
    }
}