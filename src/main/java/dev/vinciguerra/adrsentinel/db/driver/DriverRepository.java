package dev.vinciguerra.adrsentinel.db.driver;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository Spring Data JPA per la gestione dell'accesso ai dati dell'entità {@link Driver}.
 * <p>
 * L'annotazione {@code @Repository} indica a Spring che questa interfaccia funge da 
 * Data Access Object (DAO), abilitando la traduzione automatica delle eccezioni specifiche 
 * del database in eccezioni della gerarchia standard di Spring (DataAccessException).
 * <p>
 * Estendendo {@link JpaRepository}, questa interfaccia eredita nativamente, senza necessità 
 * di implementazione, tutti i metodi standard per le operazioni CRUD (Create, Read, Update, Delete), 
 * oltre alle funzionalità di paginazione e ordinamento (PagingAndSorting).
 * Il repository è tipizzato per gestire l'entità {@code Driver} avente chiave primaria di tipo {@code Long}.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
	/**
     * Ricerca e restituisce un autista all'interno del database utilizzando il suo 
     * numero di patente come criterio di filtro.
     * <p>
     * Questo metodo sfrutta il meccanismo di <i>Query Creation by Method Name</i> di Spring Data.
     * Verrà tradotto automaticamente nella query SQL: 
     * {@code SELECT * FROM driver WHERE license_number = ?}
     * <p>
     * L'utilizzo di {@link Optional} come tipo di ritorno è una best practice architetturale 
     * che permette di gestire in modo sicuro e dichiarativo l'eventualità in cui nessun autista 
     * corrisponda alla patente fornita, prevenendo l'insorgere di {@code NullPointerException} 
     * nel livello di servizio chiamante.
     * @param license la stringa che rappresenta il numero di patente esatto da ricercare
     * @return un {@code Optional} contenente l'entità {@code Driver} se trovata, 
     * oppure un {@code Optional.empty()} se nessun record corrisponde al criterio
     */
	Optional<Driver> findByLicense(String license);
	/**
     * Recupera l'elenco completo di tutti gli autisti attualmente attivi nel sistema.
     * <p>
     * Attraverso la convenzione di naming di Spring Data, il suffisso {@code True} 
     * applicato alla proprietà {@code active} genera automaticamente l'esecuzione 
     * della seguente query SQL: 
     * {@code SELECT * FROM driver WHERE active = true}
     * @return una {@link List} contenente tutte le entità {@code Driver} che hanno 
     * il flag {@code active} impostato a {@code true}. Se non ci sono autisti 
     * attivi, restituisce una lista vuota e non null.
     */
	List<Driver> findByActiveTrue();
}
