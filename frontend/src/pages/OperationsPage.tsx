import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { PackagePlus, Plus } from 'lucide-react';
import { useAuth } from '../app/authContext';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  Dialog,
  FormField,
  PageHeader,
  Pagination,
  Select,
  Table,
  TextareaField,
  useToast,
  type TableColumn,
} from '../components/ui';
import { apiRequest, ApiError } from '../lib/apiClient';
import type { PagedResponse } from '../lib/crmTypes';
import type {
  Product,
  StockMovement,
  StockMovementType,
  Supplier,
  SupplierProduct,
} from '../lib/inventoryTypes';

type OperationsTab = 'products' | 'suppliers' | 'movements';

const MANUAL_MOVEMENT_OPTIONS: { value: StockMovementType; label: string }[] = [
  { value: 'RECEIPT', label: 'Stock Receipt (+)' },
  { value: 'ADJUSTMENT_IN', label: 'Adjustment In (+)' },
  { value: 'ADJUSTMENT_OUT', label: 'Adjustment Out (-)' },
  { value: 'RETURN_IN', label: 'Customer Return (+)' },
];

export function OperationsPage() {
  const { token, hasPermission } = useAuth();
  const { notify } = useToast();
  const canWrite = hasPermission('INVENTORY_WRITE');

  const [activeTab, setActiveTab] = useState<OperationsTab>('products');

  // Products state
  const [products, setProducts] = useState<Product[]>([]);
  const [productsTotal, setProductsTotal] = useState(0);
  const [productsPage, setProductsPage] = useState(1);
  const [productSearch, setProductSearch] = useState('');
  const [lowStockFilter, setLowStockFilter] = useState('');
  const [productsLoading, setProductsLoading] = useState(true);
  const [productsError, setProductsError] = useState<string | null>(null);

  // Suppliers state
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [suppliersTotal, setSuppliersTotal] = useState(0);
  const [suppliersPage, setSuppliersPage] = useState(1);
  const [supplierSearch, setSupplierSearch] = useState('');
  const [suppliersLoading, setSuppliersLoading] = useState(false);
  const [suppliersError, setSuppliersError] = useState<string | null>(null);

  // Movements state
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [movementsTotal, setMovementsTotal] = useState(0);
  const [movementsPage, setMovementsPage] = useState(1);
  const [movementsLoading, setMovementsLoading] = useState(false);
  const [movementsError, setMovementsError] = useState<string | null>(null);

  // Create Product dialog
  const [createProductOpen, setCreateProductOpen] = useState(false);
  const [productForm, setProductForm] = useState({
    sku: '',
    name: '',
    category: '',
    unitOfMeasure: 'PCS',
    unitPrice: '0.00',
    costPrice: '',
    initialStock: '0',
    lowStockThreshold: '5',
    description: '',
  });
  const [productFormError, setProductFormError] = useState<string | null>(null);
  const [productSubmitting, setProductSubmitting] = useState(false);

  // Create Supplier dialog
  const [createSupplierOpen, setCreateSupplierOpen] = useState(false);
  const [supplierForm, setSupplierForm] = useState({
    name: '',
    contactName: '',
    email: '',
    phone: '',
    address: '',
  });
  const [supplierFormError, setSupplierFormError] = useState<string | null>(null);
  const [supplierSubmitting, setSupplierSubmitting] = useState(false);

  // Selected Product detail & stock movement dialog
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [productSuppliers, setProductSuppliers] = useState<SupplierProduct[]>([]);
  const [movementType, setMovementType] = useState<StockMovementType>('RECEIPT');
  const [movementQty, setMovementQty] = useState('');
  const [movementReason, setMovementReason] = useState('');
  const [movementReference, setMovementReference] = useState('');
  const [movementSupplierId, setMovementSupplierId] = useState('');
  const [movementError, setMovementError] = useState<string | null>(null);
  const [movementSubmitting, setMovementSubmitting] = useState(false);
  const [confirmOutOpen, setConfirmOutOpen] = useState(false);

  // Link supplier form inside product modal
  const [linkSupplierId, setLinkSupplierId] = useState('');
  const [linkSupplierSku, setLinkSupplierSku] = useState('');
  const [linkUnitCost, setLinkUnitCost] = useState('');

  // Selected Supplier detail dialog
  const [selectedSupplier, setSelectedSupplier] = useState<Supplier | null>(null);
  const [supplierLinkedProducts, setSupplierLinkedProducts] = useState<SupplierProduct[]>([]);

  const pageSize = 10;

  const fetchProducts = useCallback(async () => {
    if (!token) return;
    setProductsLoading(true);
    setProductsError(null);
    const params = new URLSearchParams({
      page: String(productsPage),
      pageSize: String(pageSize),
    });
    if (productSearch.trim()) params.set('search', productSearch.trim());
    if (lowStockFilter === 'true') params.set('lowStockOnly', 'true');

    try {
      const res = await apiRequest<PagedResponse<Product>>(
        `/api/v1/inventory/products?${params.toString()}`,
        { token },
      );
      setProducts(res.data);
      setProductsTotal(res.totalItems);
    } catch (err) {
      setProductsError(
        err instanceof ApiError ? err.message : 'Unable to load products.',
      );
    } finally {
      setProductsLoading(false);
    }
  }, [lowStockFilter, productSearch, productsPage, token]);

  const fetchSuppliers = useCallback(async () => {
    if (!token) return;
    setSuppliersLoading(true);
    setSuppliersError(null);
    const params = new URLSearchParams({
      page: String(suppliersPage),
      pageSize: String(pageSize),
    });
    if (supplierSearch.trim()) params.set('search', supplierSearch.trim());

    try {
      const res = await apiRequest<PagedResponse<Supplier>>(
        `/api/v1/inventory/suppliers?${params.toString()}`,
        { token },
      );
      setSuppliers(res.data);
      setSuppliersTotal(res.totalItems);
    } catch (err) {
      setSuppliersError(
        err instanceof ApiError ? err.message : 'Unable to load suppliers.',
      );
    } finally {
      setSuppliersLoading(false);
    }
  }, [supplierSearch, suppliersPage, token]);

  const fetchMovements = useCallback(async () => {
    if (!token) return;
    setMovementsLoading(true);
    setMovementsError(null);
    const params = new URLSearchParams({
      page: String(movementsPage),
      pageSize: String(pageSize),
    });

    try {
      const res = await apiRequest<PagedResponse<StockMovement>>(
        `/api/v1/inventory/movements?${params.toString()}`,
        { token },
      );
      setMovements(res.data);
      setMovementsTotal(res.totalItems);
    } catch (err) {
      setMovementsError(
        err instanceof ApiError
          ? err.message
          : 'Unable to load stock movement history.',
      );
    } finally {
      setMovementsLoading(false);
    }
  }, [movementsPage, token]);

  useEffect(() => {
    if (activeTab === 'products') void fetchProducts();
    else if (activeTab === 'suppliers') void fetchSuppliers();
    else void fetchMovements();
  }, [activeTab, fetchMovements, fetchProducts, fetchSuppliers]);

  const openProductDetail = async (product: Product) => {
    setSelectedProduct(product);
    setMovementError(null);
    setMovementQty('');
    setMovementReason('');
    setMovementReference('');
    setMovementSupplierId('');
    if (!token) return;

    try {
      const [links, supPage] = await Promise.all([
        apiRequest<SupplierProduct[]>(
          `/api/v1/inventory/products/${product.id}/suppliers`,
          { token },
        ),
        apiRequest<PagedResponse<Supplier>>(
          '/api/v1/inventory/suppliers?page=1&pageSize=100',
          { token },
        ),
      ]);
      setProductSuppliers(links);
      setSuppliers(supPage.data);
    } catch {
      setProductSuppliers([]);
    }
  };

  const openSupplierDetail = async (supplier: Supplier) => {
    setSelectedSupplier(supplier);
    if (!token) return;
    try {
      const links = await apiRequest<SupplierProduct[]>(
        `/api/v1/inventory/suppliers/${supplier.id}/products`,
        { token },
      );
      setSupplierLinkedProducts(links);
    } catch {
      setSupplierLinkedProducts([]);
    }
  };

  const handleCreateProduct = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    setProductFormError(null);
    setProductSubmitting(true);
    try {
      await apiRequest<Product>('/api/v1/inventory/products', {
        method: 'POST',
        token,
        body: {
          sku: productForm.sku.trim(),
          name: productForm.name.trim(),
          category: productForm.category.trim() || undefined,
          unitOfMeasure: productForm.unitOfMeasure.trim() || 'PCS',
          unitPrice: Number(productForm.unitPrice || 0),
          costPrice: productForm.costPrice ? Number(productForm.costPrice) : undefined,
          initialStock: productForm.initialStock ? Number(productForm.initialStock) : 0,
          lowStockThreshold: Number(productForm.lowStockThreshold || 0),
          description: productForm.description.trim() || undefined,
        },
      });
      setCreateProductOpen(false);
      setProductForm({
        sku: '',
        name: '',
        category: '',
        unitOfMeasure: 'PCS',
        unitPrice: '0.00',
        costPrice: '',
        initialStock: '0',
        lowStockThreshold: '5',
        description: '',
      });
      notify({
        variant: 'success',
        title: 'Product created',
        description: 'Added to organization inventory catalog.',
      });
      setProductsPage(1);
      await fetchProducts();
    } catch (err) {
      setProductFormError(
        err instanceof ApiError ? err.message : 'Unable to create product.',
      );
    } finally {
      setProductSubmitting(false);
    }
  };

  const handleCreateSupplier = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    setSupplierFormError(null);
    setSupplierSubmitting(true);
    try {
      await apiRequest<Supplier>('/api/v1/inventory/suppliers', {
        method: 'POST',
        token,
        body: {
          name: supplierForm.name.trim(),
          contactName: supplierForm.contactName.trim() || undefined,
          email: supplierForm.email.trim() || undefined,
          phone: supplierForm.phone.trim() || undefined,
          address: supplierForm.address.trim() || undefined,
        },
      });
      setCreateSupplierOpen(false);
      setSupplierForm({
        name: '',
        contactName: '',
        email: '',
        phone: '',
        address: '',
      });
      notify({
        variant: 'success',
        title: 'Supplier created',
        description: 'Supplier record saved.',
      });
      setSuppliersPage(1);
      await fetchSuppliers();
    } catch (err) {
      setSupplierFormError(
        err instanceof ApiError ? err.message : 'Unable to create supplier.',
      );
    } finally {
      setSupplierSubmitting(false);
    }
  };

  const submitMovement = async () => {
    if (!token || !selectedProduct) return;
    setMovementError(null);
    setMovementSubmitting(true);
    try {
      const movement = await apiRequest<StockMovement>(
        `/api/v1/inventory/products/${selectedProduct.id}/movements`,
        {
          method: 'POST',
          token,
          body: {
            movementType,
            quantity: Number(movementQty),
            reason: movementReason.trim(),
            reference: movementReference.trim() || undefined,
            supplierId: movementSupplierId || undefined,
          },
        },
      );
      setConfirmOutOpen(false);
      setMovementQty('');
      setMovementReason('');
      setMovementReference('');
      const refreshed = await apiRequest<Product>(
        `/api/v1/inventory/products/${selectedProduct.id}`,
        { token },
      );
      setSelectedProduct(refreshed);
      notify({
        variant: 'success',
        title: 'Stock movement recorded',
        description: `New stock: ${movement.quantityAfter} ${refreshed.unitOfMeasure}.`,
      });
      await fetchProducts();
    } catch (err) {
      setConfirmOutOpen(false);
      setMovementError(
        err instanceof ApiError
          ? err.message
          : 'Unable to record stock movement.',
      );
    } finally {
      setMovementSubmitting(false);
    }
  };

  const handleMovementSubmit = (event: FormEvent) => {
    event.preventDefault();
    setMovementError(null);
    if (!movementQty || Number(movementQty) <= 0 || !movementReason.trim()) {
      setMovementError('Enter a positive quantity and a clear movement reason.');
      return;
    }
    if (movementType === 'ADJUSTMENT_OUT') {
      setConfirmOutOpen(true);
      return;
    }
    void submitMovement();
  };

  const handleLinkSupplier = async (event: FormEvent) => {
    event.preventDefault();
    if (!token || !selectedProduct || !linkSupplierId) return;
    try {
      await apiRequest<SupplierProduct>(
        `/api/v1/inventory/products/${selectedProduct.id}/suppliers`,
        {
          method: 'POST',
          token,
          body: {
            supplierId: linkSupplierId,
            supplierSku: linkSupplierSku.trim() || undefined,
            unitCost: linkUnitCost ? Number(linkUnitCost) : undefined,
          },
        },
      );
      setLinkSupplierId('');
      setLinkSupplierSku('');
      setLinkUnitCost('');
      const links = await apiRequest<SupplierProduct[]>(
        `/api/v1/inventory/products/${selectedProduct.id}/suppliers`,
        { token },
      );
      setProductSuppliers(links);
      notify({
        variant: 'success',
        title: 'Supplier linked',
        description: 'Supplier relationship saved.',
      });
    } catch (err) {
      notify({
        variant: 'error',
        title: 'Could not link supplier',
        description:
          err instanceof ApiError ? err.message : 'Unable to link supplier.',
      });
    }
  };

  const productColumns: TableColumn<Product>[] = [
    {
      key: 'sku',
      header: 'Product / SKU',
      render: (row) => (
        <div>
          <strong>{row.name}</strong>
          <div
            style={{
              fontSize: 'var(--text-xs)',
              fontFamily: 'var(--font-mono)',
              color: 'var(--color-text-secondary)',
            }}
          >
            {row.sku}
            {row.category ? ` · ${row.category}` : ''}
          </div>
        </div>
      ),
    },
    {
      key: 'unitPrice',
      header: 'Unit Price',
      align: 'right',
      render: (row) => `$${Number(row.unitPrice).toFixed(2)}`,
    },
    {
      key: 'stockQuantity',
      header: 'On Hand',
      align: 'right',
      render: (row) => `${row.stockQuantity} ${row.unitOfMeasure}`,
    },
    {
      key: 'status',
      header: 'Stock Status',
      render: (row) =>
        !row.active ? (
          <Badge variant="neutral">Inactive</Badge>
        ) : row.lowStock ? (
          <Badge variant="warning">Low Stock (≤ {row.lowStockThreshold})</Badge>
        ) : (
          <Badge variant="success">In Stock</Badge>
        ),
    },
  ];

  const supplierColumns: TableColumn<Supplier>[] = [
    {
      key: 'name',
      header: 'Supplier',
      render: (row) => (
        <div>
          <strong>{row.name}</strong>
          {row.contactName ? (
            <div
              style={{
                fontSize: 'var(--text-xs)',
                color: 'var(--color-text-secondary)',
              }}
            >
              {row.contactName}
            </div>
          ) : null}
        </div>
      ),
    },
    {
      key: 'email',
      header: 'Email',
      render: (row) => row.email ?? '—',
    },
    {
      key: 'phone',
      header: 'Phone',
      render: (row) => row.phone ?? '—',
    },
    {
      key: 'active',
      header: 'Status',
      render: (row) => (
        <Badge variant={row.active ? 'success' : 'neutral'}>
          {row.active ? 'Active' : 'Inactive'}
        </Badge>
      ),
    },
  ];

  const movementColumns: TableColumn<StockMovement>[] = [
    {
      key: 'createdAt',
      header: 'Timestamp',
      render: (row) => new Date(row.createdAt).toLocaleString(),
    },
    {
      key: 'product',
      header: 'Product',
      render: (row) => (
        <div>
          <strong>{row.productName}</strong>
          <div
            style={{
              fontSize: 'var(--text-xs)',
              fontFamily: 'var(--font-mono)',
              color: 'var(--color-text-secondary)',
            }}
          >
            {row.productSku}
          </div>
        </div>
      ),
    },
    {
      key: 'movementType',
      header: 'Type',
      render: (row) => (
        <Badge variant={row.quantityDelta > 0 ? 'success' : 'warning'}>
          {row.movementType}
        </Badge>
      ),
    },
    {
      key: 'delta',
      header: 'Change',
      align: 'right',
      render: (row) =>
        row.quantityDelta > 0 ? `+${row.quantityDelta}` : `${row.quantityDelta}`,
    },
    {
      key: 'balance',
      header: 'Before → After',
      align: 'right',
      render: (row) => `${row.quantityBefore} → ${row.quantityAfter}`,
    },
    {
      key: 'reason',
      header: 'Actor & Reason',
      render: (row) => (
        <div>
          <div>{row.reason}</div>
          <div
            style={{
              fontSize: 'var(--text-xs)',
              color: 'var(--color-text-secondary)',
            }}
          >
            {row.actorName ?? 'System'}
            {row.reference ? ` · Ref: ${row.reference}` : ''}
          </div>
        </div>
      ),
    },
  ];

  return (
    <div className="foundation-stack">
      <PageHeader
        title="Operations & Inventory"
        subtitle="Product catalog, supplier relationships, stock levels, and auditable stock movements."
        breadcrumbs={[
          { label: 'Workspace', to: '/dashboard' },
          { label: 'Operations' },
        ]}
        badge={<Badge variant="success">Active · Prompt 04</Badge>}
        actions={
          canWrite ? (
            activeTab === 'suppliers' ? (
              <Button
                variant="primary"
                leftIcon={<Plus size={16} aria-hidden="true" />}
                onClick={() => setCreateSupplierOpen(true)}
              >
                New Supplier
              </Button>
            ) : (
              <Button
                variant="primary"
                leftIcon={<PackagePlus size={16} aria-hidden="true" />}
                onClick={() => setCreateProductOpen(true)}
              >
                New Product
              </Button>
            )
          ) : null
        }
      />

      <div className="foundation-row" role="tablist" aria-label="Operations views">
        <Button
          role="tab"
          aria-selected={activeTab === 'products'}
          variant={activeTab === 'products' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('products')}
        >
          Products
        </Button>
        <Button
          role="tab"
          aria-selected={activeTab === 'suppliers'}
          variant={activeTab === 'suppliers' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('suppliers')}
        >
          Suppliers
        </Button>
        <Button
          role="tab"
          aria-selected={activeTab === 'movements'}
          variant={activeTab === 'movements' ? 'primary' : 'secondary'}
          onClick={() => setActiveTab('movements')}
        >
          Movement History
        </Button>
      </div>

      {activeTab === 'products' ? (
        <Card>
          <CardContent className="foundation-stack">
            <div className="foundation-row" style={{ alignItems: 'flex-end' }}>
              <div style={{ flex: '1 1 16rem' }}>
                <FormField
                  label="Search Products"
                  placeholder="SKU, name, or category..."
                  value={productSearch}
                  onChange={(event) => {
                    setProductSearch(event.target.value);
                    setProductsPage(1);
                  }}
                />
              </div>
              <div style={{ minWidth: '12rem' }}>
                <Select
                  label="Stock Filter"
                  value={lowStockFilter}
                  options={[
                    { value: '', label: 'All products' },
                    { value: 'true', label: 'Low stock only' },
                  ]}
                  onChange={(event) => {
                    setLowStockFilter(event.target.value);
                    setProductsPage(1);
                  }}
                />
              </div>
            </div>

            <Table
              caption="Inventory products"
              columns={productColumns}
              rows={products}
              getRowKey={(row) => row.id}
              loading={productsLoading}
              error={productsError}
              onRetry={() => void fetchProducts()}
              emptyTitle="No products found"
              emptyDescription="Create a product with its SKU, unit of measure, and low-stock threshold."
              onRowClick={(row) => void openProductDetail(row)}
            />

            {!productsLoading && !productsError && productsTotal > 0 ? (
              <Pagination
                page={productsPage}
                pageSize={pageSize}
                totalItems={productsTotal}
                onPageChange={setProductsPage}
              />
            ) : null}
          </CardContent>
        </Card>
      ) : activeTab === 'suppliers' ? (
        <Card>
          <CardContent className="foundation-stack">
            <FormField
              label="Search Suppliers"
              placeholder="Name, contact, or email..."
              value={supplierSearch}
              onChange={(event) => {
                setSupplierSearch(event.target.value);
                setSuppliersPage(1);
              }}
            />

            <Table
              caption="Suppliers"
              columns={supplierColumns}
              rows={suppliers}
              getRowKey={(row) => row.id}
              loading={suppliersLoading}
              error={suppliersError}
              onRetry={() => void fetchSuppliers()}
              emptyTitle="No suppliers found"
              emptyDescription="Add a supplier to track product sourcing and stock receipts."
              onRowClick={(row) => void openSupplierDetail(row)}
            />

            {!suppliersLoading && !suppliersError && suppliersTotal > 0 ? (
              <Pagination
                page={suppliersPage}
                pageSize={pageSize}
                totalItems={suppliersTotal}
                onPageChange={setSuppliersPage}
              />
            ) : null}
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="foundation-stack">
            <Table
              caption="Stock movement history"
              columns={movementColumns}
              rows={movements}
              getRowKey={(row) => row.id}
              loading={movementsLoading}
              error={movementsError}
              onRetry={() => void fetchMovements()}
              emptyTitle="No stock movements recorded"
              emptyDescription="Every initial balance, receipt, adjustment, and deduction appears here with actor and timestamp."
            />

            {!movementsLoading && !movementsError && movementsTotal > 0 ? (
              <Pagination
                page={movementsPage}
                pageSize={pageSize}
                totalItems={movementsTotal}
                onPageChange={setMovementsPage}
              />
            ) : null}
          </CardContent>
        </Card>
      )}

      {/* Create Product Dialog */}
      <Dialog
        open={createProductOpen}
        onClose={() => setCreateProductOpen(false)}
        title="Create Product"
        size="lg"
      >
        <form onSubmit={handleCreateProduct} className="foundation-stack" noValidate>
          {productFormError ? (
            <Alert variant="error" title="Could not create product" description={productFormError} />
          ) : null}
          <div className="foundation-row">
            <FormField
              label="SKU"
              required
              placeholder="SKU-1001"
              value={productForm.sku}
              onChange={(event) => setProductForm({ ...productForm, sku: event.target.value })}
            />
            <FormField
              label="Product Name"
              required
              value={productForm.name}
              onChange={(event) => setProductForm({ ...productForm, name: event.target.value })}
            />
          </div>
          <div className="foundation-row">
            <FormField
              label="Category"
              value={productForm.category}
              onChange={(event) => setProductForm({ ...productForm, category: event.target.value })}
            />
            <FormField
              label="Unit of Measure"
              required
              value={productForm.unitOfMeasure}
              onChange={(event) =>
                setProductForm({ ...productForm, unitOfMeasure: event.target.value })
              }
            />
          </div>
          <div className="foundation-row">
            <FormField
              label="Unit Price"
              type="number"
              step="0.01"
              min="0"
              required
              value={productForm.unitPrice}
              onChange={(event) =>
                setProductForm({ ...productForm, unitPrice: event.target.value })
              }
            />
            <FormField
              label="Initial Stock"
              type="number"
              step="0.001"
              min="0"
              value={productForm.initialStock}
              onChange={(event) =>
                setProductForm({ ...productForm, initialStock: event.target.value })
              }
            />
            <FormField
              label="Low-Stock Threshold"
              type="number"
              step="0.001"
              min="0"
              required
              value={productForm.lowStockThreshold}
              onChange={(event) =>
                setProductForm({ ...productForm, lowStockThreshold: event.target.value })
              }
            />
          </div>
          <TextareaField
            label="Description"
            value={productForm.description}
            onChange={(event) =>
              setProductForm({ ...productForm, description: event.target.value })
            }
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" onClick={() => setCreateProductOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={productSubmitting}>
              Create Product
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Create Supplier Dialog */}
      <Dialog
        open={createSupplierOpen}
        onClose={() => setCreateSupplierOpen(false)}
        title="Create Supplier"
        size="lg"
      >
        <form onSubmit={handleCreateSupplier} className="foundation-stack" noValidate>
          {supplierFormError ? (
            <Alert variant="error" title="Could not create supplier" description={supplierFormError} />
          ) : null}
          <FormField
            label="Supplier Name"
            required
            value={supplierForm.name}
            onChange={(event) => setSupplierForm({ ...supplierForm, name: event.target.value })}
          />
          <FormField
            label="Contact Name"
            value={supplierForm.contactName}
            onChange={(event) =>
              setSupplierForm({ ...supplierForm, contactName: event.target.value })
            }
          />
          <div className="foundation-row">
            <FormField
              label="Email"
              type="email"
              value={supplierForm.email}
              onChange={(event) => setSupplierForm({ ...supplierForm, email: event.target.value })}
            />
            <FormField
              label="Phone"
              value={supplierForm.phone}
              onChange={(event) => setSupplierForm({ ...supplierForm, phone: event.target.value })}
            />
          </div>
          <TextareaField
            label="Address"
            value={supplierForm.address}
            onChange={(event) =>
              setSupplierForm({ ...supplierForm, address: event.target.value })
            }
          />
          <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" onClick={() => setCreateSupplierOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={supplierSubmitting}>
              Create Supplier
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Product Detail + Stock Movement Dialog */}
      <Dialog
        open={Boolean(selectedProduct)}
        onClose={() => setSelectedProduct(null)}
        title={selectedProduct ? `${selectedProduct.name} (${selectedProduct.sku})` : 'Product'}
        description={
          selectedProduct
            ? `On hand: ${selectedProduct.stockQuantity} ${selectedProduct.unitOfMeasure} · Threshold: ${selectedProduct.lowStockThreshold}`
            : undefined
        }
        size="lg"
      >
        {selectedProduct ? (
          <div className="foundation-stack">
            <div className="foundation-row">
              <Badge variant={selectedProduct.lowStock ? 'warning' : 'success'} size="md">
                {selectedProduct.lowStock ? 'Low Stock' : 'Healthy Stock'}
              </Badge>
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--color-text-secondary)' }}>
                Unit Price: ${Number(selectedProduct.unitPrice).toFixed(2)}
              </span>
            </div>

            {canWrite ? (
              <form onSubmit={handleMovementSubmit} className="foundation-stack">
                <strong style={{ fontSize: 'var(--text-sm)' }}>Record Stock Movement</strong>
                {movementError ? (
                  <Alert variant="error" title="Movement rejected" description={movementError} />
                ) : null}
                <div className="foundation-row">
                  <Select
                    label="Movement Type"
                    value={movementType}
                    options={MANUAL_MOVEMENT_OPTIONS}
                    onChange={(event) =>
                      setMovementType(event.target.value as StockMovementType)
                    }
                  />
                  <FormField
                    label="Quantity"
                    type="number"
                    step="0.001"
                    min="0.001"
                    required
                    value={movementQty}
                    onChange={(event) => setMovementQty(event.target.value)}
                  />
                </div>
                <div className="foundation-row">
                  <FormField
                    label="Reason"
                    required
                    placeholder="e.g. Supplier delivery or physical count"
                    value={movementReason}
                    onChange={(event) => setMovementReason(event.target.value)}
                  />
                  <FormField
                    label="Reference (Optional)"
                    placeholder="e.g. PO-1024"
                    value={movementReference}
                    onChange={(event) => setMovementReference(event.target.value)}
                  />
                </div>
                {suppliers.length > 0 ? (
                  <Select
                    label="Supplier (Optional)"
                    value={movementSupplierId}
                    options={[
                      { value: '', label: 'None' },
                      ...suppliers.map((s) => ({ value: s.id, label: s.name })),
                    ]}
                    onChange={(event) => setMovementSupplierId(event.target.value)}
                  />
                ) : null}
                <div className="foundation-row" style={{ justifyContent: 'flex-end' }}>
                  <Button type="submit" variant="primary" loading={movementSubmitting}>
                    Apply Movement
                  </Button>
                </div>
              </form>
            ) : null}

            <div className="foundation-stack">
              <strong style={{ fontSize: 'var(--text-sm)' }}>Linked Suppliers</strong>
              {productSuppliers.length === 0 ? (
                <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                  No suppliers linked to this product yet.
                </span>
              ) : (
                productSuppliers.map((link) => (
                  <div
                    key={link.id}
                    style={{
                      padding: 'var(--space-2) var(--space-3)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-md)',
                      fontSize: 'var(--text-sm)',
                    }}
                  >
                    <strong>{link.supplierName}</strong>
                    {link.supplierSku ? ` · Supplier SKU: ${link.supplierSku}` : ''}
                    {link.unitCost !== null ? ` · Cost: $${Number(link.unitCost).toFixed(2)}` : ''}
                  </div>
                ))
              )}

              {canWrite && suppliers.length > 0 ? (
                <form onSubmit={handleLinkSupplier} className="foundation-row" style={{ alignItems: 'flex-end' }}>
                  <Select
                    label="Link Supplier"
                    value={linkSupplierId}
                    options={[
                      { value: '', label: 'Select supplier...' },
                      ...suppliers.map((s) => ({ value: s.id, label: s.name })),
                    ]}
                    onChange={(event) => setLinkSupplierId(event.target.value)}
                  />
                  <FormField
                    label="Supplier SKU"
                    value={linkSupplierSku}
                    onChange={(event) => setLinkSupplierSku(event.target.value)}
                  />
                  <FormField
                    label="Unit Cost"
                    type="number"
                    step="0.01"
                    min="0"
                    value={linkUnitCost}
                    onChange={(event) => setLinkUnitCost(event.target.value)}
                  />
                  <Button type="submit" variant="secondary" disabled={!linkSupplierId}>
                    Link
                  </Button>
                </form>
              ) : null}
            </div>
          </div>
        ) : null}
      </Dialog>

      {/* Confirm Negative Adjustment Dialog */}
      <Dialog
        open={confirmOutOpen}
        onClose={() => setConfirmOutOpen(false)}
        title="Confirm Stock Deduction"
        description="Negative stock adjustments permanently reduce on-hand inventory and record an audit entry."
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmOutOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={movementSubmitting}
              onClick={() => void submitMovement()}
            >
              Confirm Deduction
            </Button>
          </>
        }
      >
        <p style={{ color: 'var(--color-text-secondary)' }}>
          Deduct <strong>{movementQty}</strong> {selectedProduct?.unitOfMeasure} from{' '}
          <strong>{selectedProduct?.sku}</strong> for reason: &quot;{movementReason}&quot;?
        </p>
      </Dialog>

      {/* Supplier Detail Dialog */}
      <Dialog
        open={Boolean(selectedSupplier)}
        onClose={() => setSelectedSupplier(null)}
        title={selectedSupplier?.name ?? 'Supplier'}
        description={selectedSupplier?.email ?? undefined}
      >
        {selectedSupplier ? (
          <div className="foundation-stack">
            <strong style={{ fontSize: 'var(--text-sm)' }}>Supplied Products</strong>
            {supplierLinkedProducts.length === 0 ? (
              <span style={{ fontSize: 'var(--text-xs)', color: 'var(--color-text-secondary)' }}>
                No products linked to this supplier yet.
              </span>
            ) : (
              supplierLinkedProducts.map((link) => (
                <div
                  key={link.id}
                  style={{
                    padding: 'var(--space-2) var(--space-3)',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-md)',
                    fontSize: 'var(--text-sm)',
                  }}
                >
                  <strong>{link.productName}</strong> ({link.productSku})
                </div>
              ))
            )}
          </div>
        ) : null}
      </Dialog>
    </div>
  );
}

