package org.gbif.registry.metasync.api;

import org.gbif.api.model.registry.Dataset;
import org.gbif.api.model.registry.Endpoint;

import java.util.List;
import java.util.UUID;

/**
 * This interface specifies all operations necessary to do a synchronisation of an Installation from our registry
 * against the actual Endpoint.
 */
public interface MetadataSynchroniser {

  /**
   * Runs a synchronisation against the installation provided.
   * <p/>
   * All datasets from this installation will be updated, old ones deleted and new ones registered if needed.
   *
   * @param key of the installation to synchronise
   * @return the result of the synchronisation
   * @throws IllegalArgumentException if no technical installation with the given key exists, the installation doesn't
   *                                  have any endpoints or none that we can use to gather metadata (might also be
   *                                  missing the proper protocol handler)
   */
  SyncResult synchroniseInstallation(UUID key);

  /**
   * Synchronises all registered Installations ignoring any failures (because there will definitely be failures due to
   * unsupported Installation types and missing Endpoints). This will run single threaded.
   */
  List<SyncResult> synchroniseAllInstallations();

  /**
   * Synchronises all registered Installations ignoring any failures (because there will definitely be failures due to
   * unsupported Installation types and missing Endpoints).
   *
   * @param parallel how many threads to run in parallel
   */
  List<SyncResult> synchroniseAllInstallations(int parallel);

  /**
   * Retrieve a count of records held in this dataset.
   *
   * @param dataset
   * @return the count, or null.
   */
  Long getDatasetCount(Dataset dataset, Endpoint endpoint) throws MetadataException;
}
