# Model Code Generation Bundle

## Overview

This bundle contains Eclipse launch configuration files for generating EMF (Eclipse Modeling Framework) model code. These launch configurations are used to run MWE2 (Modeling Workflow Engine 2) workflows that generate Java code from EMF model definitions.

## Purpose

The launch configurations in this bundle automate the code generation process for openHAB's domain-specific language (DSL) models, including:

- **Items Model**: Code generation for item definitions
- **Sitemap Model**: Code generation for sitemap definitions
- **Script Model**: Code generation for script definitions
- **Rule Model**: Code generation for rule definitions
- **Persistence Model**: Code generation for persistence configuration definitions
- **Thing Model**: Code generation for thing definitions

## Launch Configurations

### Individual Model Generation

1. **1 Generate Items Model.launch** - Generates code for the items model
2. **2 Generate Sitemap Model.launch** - Generates code for the sitemap model
3. **3 Generate Script Model.launch** - Generates code for the script model
4. **4 Generate Rule Model.launch** - Generates code for the rule model
5. **5 Generate Persistence Model.launch** - Generates code for the persistence model
6. **6 Generate Thing Model.launch** - Generates code for the thing model

### Batch Generation

- **Generate All Models.launch** - Executes all individual model generation tasks sequentially

## Usage

These launch configurations are intended to be used within the Eclipse IDE with the EMF and Xtext plugins installed. They can be executed from the Eclipse Run Configurations dialog.

## Configuration Details

All launch configurations use:
- **Main Type**: `org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher`
- **VM Arguments**: `-Xmx512m` (512MB heap size)
- **Project**: `org.openhab.core.model.codegen`

Each configuration points to a specific MWE2 workflow file in the corresponding model bundle.

## Notes

- These launch configurations are IDE-specific and are not part of the runtime bundle
- The generated code is placed in the respective model bundles (e.g., `org.openhab.core.model.item.runtime`)
- Regeneration is typically required when EMF model definitions are modified

