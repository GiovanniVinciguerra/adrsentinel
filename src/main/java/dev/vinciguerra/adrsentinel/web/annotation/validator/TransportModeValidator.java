package dev.vinciguerra.adrsentinel.web.annotation.validator;

import dev.vinciguerra.adrsentinel.db.onunumber.TransportMode;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorTransportMode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta (Validator Engine) per il vincolo custom {@link ValidatorTransportMode}.
 * <p>
 * Questa classe funge da barriera difensiva primaria (Anti-Corruption Layer) ai confini 
 * del Presentation Layer. Il suo scopo è validare che la stringa fornita dal client 
 * all'interno del payload JSON corrisponda esattamente a una delle costanti definite 
 * nell'enumerazione di dominio {@link TransportMode} (es. "PACKAGES", "TANK", "BULK").
 * </p>
 * <p>
 * <b>Vantaggio Architetturale:</b> Ricevere una {@code String} dal client e validarla 
 * dinamicamente tramite questo componente previene le eccezioni di deserializzazione 
 * del framework (es. {@code HttpMessageNotReadableException} di Jackson). Ciò permette 
 * all'applicazione di intercettare valori anomali in modo aggraziato e restituire 
 * al chiamante un HTTP 400 Bad Request strutturato e descrittivo.
 * </p>
 * <p>
 * <b>Thread-Safety:</b> Essendo gestito dall'infrastruttura di validazione di Spring 
 * (Hibernate Validator), questo componente è un Singleton senza stato (Stateless) 
 * ed è intrinsecamente Thread-Safe.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorTransportMode
 */
public class TransportModeValidator implements ConstraintValidator<ValidatorTransportMode, String> {
	/**
	 * Esegue la validazione della stringa in ingresso applicando un imbuto logico 
	 * (Fail-Fast pattern) a tre stadi.
	 * <ol>
	 * <li><b>Null-Safety:</b> Rifiuta esplicitamente i payload privi di valore. 
	 * Questo rende l'annotazione autosufficiente, fungendo implicitamente anche da {@code @NotNull}.</li>
	 * <li><b>Domain Matching (Enum Resolution):</b> Tenta la risoluzione dinamica della stringa 
	 * verso la classe {@link TransportMode}. Se la stringa contiene refusi, valori non 
	 * ammessi o differenze di casing (es. "Tank" invece di "TANK"), la JVM solleverà 
	 * una {@code IllegalArgumentException}, che viene catturata e convertita in una 
	 * violazione del vincolo ({@code return false}).</li>
	 * <li><b>Boundary Check (Database Protection):</b> Come misura di sicurezza finale, 
	 * verifica che la lunghezza della stringa non ecceda il limite standard dei 
	 * campi VARCHAR (255 caratteri), prevenendo potenziali eccezioni SQL in fase di persistenza.</li>
	 * </ol>
	 * @param value   la stringa estratta dal payload JSON che rappresenta la modalità 
	 * di trasporto desiderata dal client.
	 * @param context il contesto operativo di validazione, utile per sovrascrivere 
	 * dinamicamente i template dei messaggi di errore qualora necessario.
	 * @return {@code true} se la stringa è valorizzata, corrisponde a un TransportMode 
	 * valido e rispetta i limiti di lunghezza; {@code false} altrimenti.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		try {
			Enum.valueOf(TransportMode.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
