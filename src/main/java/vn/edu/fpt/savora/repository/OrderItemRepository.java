package vn.edu.fpt.savora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.savora.entity.OrderItem;
import vn.edu.fpt.savora.enums.OrderItemStatus;
import vn.edu.fpt.savora.enums.OrderStatus;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    List<OrderItem> findByOrder_OrderIdOrderByCreatedAtDesc(Integer orderId);

    List<OrderItem> findByDish_DishId(Integer dishId);

    @Query("SELECT oi FROM OrderItem oi WHERE oi.itemStatus IN :itemStatuses AND oi.order.status IN :orderStatuses ORDER BY oi.createdAt ASC")
    List<OrderItem> findKitchenQueueItems(
            @Param("itemStatuses") Collection<OrderItemStatus> itemStatuses,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses
    );
}
