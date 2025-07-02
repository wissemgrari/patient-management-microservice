package com.pm.patientservice.controller;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.dto.validator.CreatePatientValidationGroup;
import com.pm.patientservice.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
@Tag(
  name = "Patient",
  description = "API for managing patients"
)
public class PatientController {
  private final PatientService patientService;
  
  public PatientController(PatientService patientService) {
    this.patientService = patientService;
  }
  
  @GetMapping
  @Operation(summary = "Get all patients")
  public ResponseEntity<List<PatientResponseDTO>> findAll() {
    List<PatientResponseDTO> patients = patientService.getPatients();
    return ResponseEntity.ok().body(patients);
  }
  
  @PostMapping
  @Operation(summary = "Create a new patient")
  public ResponseEntity<PatientResponseDTO> createPatient(
    @Validated({Default.class, CreatePatientValidationGroup.class}) @RequestBody PatientRequestDTO patientRequestDTO) {
    PatientResponseDTO patientResponseDTO = patientService.createPatient(patientRequestDTO);
    return ResponseEntity.ok().body(patientResponseDTO);
  }
  
  @PutMapping("/{id}")
  @Operation(summary = "Update an existing patient")
  public ResponseEntity<PatientResponseDTO> updatePatient(@PathVariable("id") UUID id,
                                                          @Validated({Default.class}) @RequestBody
                                                          PatientRequestDTO patientRequestDTO) {
    PatientResponseDTO patientResponseDTO = patientService.updatePatient(id, patientRequestDTO);
    return ResponseEntity.status(HttpStatus.CREATED).body(patientResponseDTO);
  }
  
  @DeleteMapping("/{id}")
  @Operation(summary = "Delete a patient")
  public ResponseEntity<Void> deletePatient(@PathVariable("id") UUID id) {
    patientService.deletePatient(id);
    return ResponseEntity.noContent().build();
  }
}
