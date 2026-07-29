package dev.vinciguerra.adrsentinel.web.dto.driver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshot;

/**
 * Data Transfer Object (DTO) immutabile che modella il payload di risposta (Response Payload) 
 * restituito al client dalle API REST relative alla risorsa Conducente (Driver).
 * <p><b>Ruolo Architetturale:</b></p>
 * <p>Questo record agisce come maschera di uscita (Outbound Mask) per l'entità di dominio. 
 * Isola il client dai dettagli implementativi del database e della logica di business, 
 * impedendo l'esposizione accidentale di campi sensibili non destinati alla vista 
 * (es. ID interni, versioning di Hibernate, date di creazione record) e serializzando 
 * i tipi complessi in formati di facile consumo per il frontend (JSON stringificato).</p>
 * @param fullName Il nome e cognome completo del conducente.
 * @param taxCode Il numero di identificazione fiscale (es. Codice Fiscale).
 * @param phoneNumber Il recapito telefonico formattato.
 * @param license Il numero della patente di guida.
 * @param licenseExpireDate La data di scadenza della patente, serializzata in formato stringa ISO-8601 (YYYY-MM-DD).
 * @param cqcExpireDate La data di scadenza della CQC, serializzata in formato stringa ISO-8601.
 * @param driverApprovals L'insieme delle certificazioni/abilitazioni possedute dal conducente, espresse come stringhe (nomi delle Enum).
 * @param active Flag booleano che indica se il profilo del conducente è attualmente operativo/abilitato a sistema.
 * @param inTrnasit Flag booleano che indica se il conducente è attualmente impegnato in un viaggio.
 * @param historicalData Flag booleano che indica se il record restituito fa parte di un archivio storico (snapshot) 
 * piuttosto che rappresentare lo stato anagrafico corrente.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record DriverResponseDTO(String fullName, String taxCode, String phoneNumber, String license, String licenseExpireDate,
		String cqcExpireDate, Set<String> driverApprovals, Boolean active, Boolean inTransit, Boolean historicalData) {
	
	/**
	 * Pattern "Static Factory Method" che converte un'entità di dominio {@link Driver} 
	 * nella sua rappresentazione DTO destinata alla serializzazione verso il client.
	 * <p><b>Dettaglio delle conversioni applicate (Entity -> DTO):</b></p>
	 * <ul>
	 * <li><b>Safe Null-Check:</b> Se l'entità in ingresso è {@code null}, il metodo restituisce {@code null} 
	 * in modo sicuro, evitando {@code NullPointerException} durante il mapping di liste o relazioni opzionali.</li>
	 * <li><b>De-tipizzazione Date:</b> Le proprietà {@code LocalDate} vengono convertite in {@code String} 
	 * richiamando il metodo {@code toString()}, che garantisce lo standard ISO-8601 nativo di Java.</li>
	 * <li><b>De-tipizzazione Enum:</b> I valori enumerati di {@code driverApprovals} vengono estratti 
	 * in stringhe primitive usando il metodo {@code name()}, preservandone l'esatta nomenclatura (case-sensitive).</li>
	 * <li><b>Business Logic Implicita:</b> Il campo {@code historicalData} viene forzato rigidamente a {@code false}. 
	 * Si assume architetturalmente che questo mapper venga utilizzato esclusivamente per l'estrazione di dati "vivi" e correnti.</li>
	 * </ul>
	 * @param entity L'entità di dominio {@link Driver} estratta dal layer di persistenza (Database).
	 * @return Un'istanza popolata di {@link DriverResponseDTO}, o {@code null} se l'entità fornita è nulla.
	 */
	public static DriverResponseDTO fromEntity(Driver entity) {
		if(entity == null)
			return null;
		
		return new DriverResponseDTO(
			entity.getFullName(),
			entity.getTaxCode(),
			entity.getPhoneNumber(),
			entity.getLicense(),
			entity.getLicenseExpireDate().toString(),
			entity.getCqcExpireDate().toString(),
			entity.getDriverApprovals().stream()
				.map(Enum::name)
				.collect(Collectors.toSet()),
			entity.isActive(),
			entity.isInTransit(),
			false
		);
	}
	
	/**
	 * Pattern "Static Factory Method" in overloading che converte un'entità storica 
	 * {@link DriverSnapshot} nella sua rappresentazione DTO unificata destinata al client.
	 * <p><b>Dettaglio delle conversioni applicate (Snapshot -> DTO):</b></p>
	 * <ul>
	 * <li><b>Safe Null-Check:</b> Se l'entità in ingresso è {@code null}, il metodo restituisce {@code null} 
	 * in modo sicuro, allineandosi al comportamento dell'omologo per le entità master.</li>
	 * <li><b>De-serializzazione Approvazioni (Unflattening):</b> A livello di database, lo snapshot storicizza 
	 * le abilitazioni appiattendole in un'unica stringa comma-separated (CSV) o nel marker {@code "NONE"}. 
	 * Questo blocco di logica inverte il processo (Unflattening): se individua il marker "NONE", istanzia 
	 * un Set vuoto ({@code HashSet}); altrimenti, esegue lo split per la virgola, ricostruendo l'originale 
	 * collezione {@code Set<String>} attesa dal client.</li>
	 * <li><b>De-tipizzazione Date:</b> Le scadenze cristallizzate ({@code LocalDate}) vengono serializzate 
	 * nello standard ISO-8601 tramite il metodo {@code toString()}.</li>
	 * <li><b>Soppressione Flag di Ciclo di Vita:</b> Poiché lo snapshot rappresenta un "manifest" congelato 
	 * nel tempo associato a una specifica spedizione, i parametri operativi legati allo stato di salute 
	 * corrente dell'autista ({@code active} e {@code inTransit}) perdono di valore contestuale e vengono 
	 * forzatamente omessi (impostati a {@code null}). Questo evita di veicolare al frontend informazioni 
	 * di stato fuorvianti.</li>
	 * <li><b>Business Logic Implicita:</b> Il campo {@code historicalData} viene forzato rigidamente 
	 * a {@code true}. Questo segnale architettonico indica inequivocabilmente al client che i dati in ricezione 
	 * rappresentano un reperto d'archivio in sola lettura (read-only) e non l'anagrafica "viva" dell'autista.</li>
	 * </ul>
	 * @param entity L'entità storica {@link DriverSnapshot} estratta dal layer di persistenza, 
	 * rappresentante la fotografia del conducente nell'esatto istante in cui 
	 * la spedizione ha abbandonato la fase di pianificazione.
	 * @return Un'istanza popolata di {@link DriverResponseDTO}, o {@code null} se l'entità fornita è nulla.
	 */
	public static DriverResponseDTO fromEntity(DriverSnapshot entity) {
		if(entity == null)
			return null;
		
		Set<String> approvals;
		if(entity.getDriverApprovalSnap().equals("NONE"))
			approvals = new HashSet<String>();
		else {
			approvals = Arrays
				.stream(
					entity.getDriverApprovalSnap()
					.split(",")
				)
				.collect(Collectors.toSet());
		}
		return new DriverResponseDTO(
			entity.getFullNameSnap(),
			entity.getTaxCodeSnap(),
			entity.getPhoneNumberSnap(),
			entity.getLicenseSnap(),
			entity.getLicenseExpireDateSnap().toString(),
			entity.getCqcExpireDateSnap().toString(),
			approvals,
			null,
			null,
			true
		);
	}
}
