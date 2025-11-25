
-- Sample data for ChefAI database with 16-byte blob UUIDs

-- Test User
INSERT INTO `users` (`uuid`, `displayName`, `email`, `avatarUrl`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'F47AC10B58CC4372A5670E02B2C3D479', 'Test User', 'test.user@example.com', 'https://lh3.googleusercontent.com/a/ACg8ocJkiHEp4Du1l-Y4-raRQ6opmwP2Dihq9JIi47wJUn1Aki3d_8Z6=s288-c-no', 1672531200, null, 'SYNCED');

-- Ingredients
INSERT INTO `ingredients` (`uuid`, `displayName`, `allergenId`, `sourcePrimaryId`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'01C7B3E3802C4B6E8E5F2B8E3A3F5C7C', 'Spaghetti', null, null, 1672531200, null, 'SYNCED'),
(X'A2B8D4E05B274D9F8C3E9B6E2D1C3A4F', 'Guanciale', null, null, 1672531200, null, 'SYNCED'),
(X'C3E9A4F23B6D4C8E9F2A1C8B4D6E2A1B', 'Eggs', null, null, 1672531200, null, 'SYNCED'),
(X'D4B3A2C18E9F4B7D6A5C4D3B2A1C8E9F', 'Pecorino Romano Cheese', null, null, 1672531200, null, 'SYNCED'),
(X'E5F2A1B09C8D4E6F7B4A3C2D1B8E9F7A', 'Black Pepper', null, null, 1672531200, null, 'SYNCED'),
(X'F6A1B2C3D4E54F6A8B9C0D1E2F3A4B5C', 'Chicken Breast', null, null, 1672531200, null, 'SYNCED'),
(X'17B2C3D4E5F64A8B9C0D1E2F3A4B5C6D', 'Broccoli', null, null, 1672531200, null, 'SYNCED'),
(X'28C3D4E5F6A74B9C8D1E2F3A4B5C6D7E', 'Garlic', null, null, 1672531200, null, 'SYNCED'),
(X'39D4E5F6A7B84C0D9E2F3A4B5C6D7E8F', 'Olive Oil', null, null, 1672531200, null, 'SYNCED'),
(X'4A5E6F7AB8C94D1E8F3A4B5C6D7E8F9A', 'Salmon Fillet', null, null, 1672531200, null, 'SYNCED'),
(X'5B6F7A8BC9D04E2F9A4B5C6D7E8F9A0B', 'Lemon', null, null, 1672531200, null, 'SYNCED'),
(X'6C7A8B9CD0E14F3AAB5C6D7E8F9A0B1C', 'Dill', null, null, 1672531200, null, 'SYNCED'),
(X'7D8B9C0DE1F24A4BBC6D7E8F9A0B1C2D', 'Beef Mince', null, null, 1672531200, null, 'SYNCED'),
(X'8E9C0D1EF2A34B5CCD7E8F9A0B1C2D3E', 'Onion', null, null, 1672531200, null, 'SYNCED'),
(X'9F0D1E2FA3B44C6DDE8F9A0B1C2D3E4F', 'Tomatoes', null, null, 1672531200, null, 'SYNCED'),
(X'A01E2F3AB4C54D7EEF9A0B1C2D3E4F5A', 'Kidney Beans', null, null, 1672531200, null, 'SYNCED'),
(X'B12F3A4BC5D64E8FF0AB1C2D3E4F5A6B', 'Chilli Powder', null, null, 1672531200, null, 'SYNCED'),
(X'C23A4B5CD6E74F9A01BC2D3E4F5A6B7C', 'Avocado', null, null, 1672531200, null, 'SYNCED'),
(X'D34B5C6DE7F84A0B12CD3E4F5A6B7C8D', 'Coriander', null, null, 1672531200, null, 'SYNCED'),
(X'E45C6D7EF8A94B1C23DE4F5A6B7C8D9E', 'Lime', null, null, 1672531200, null, 'SYNCED');

-- Labels
INSERT INTO `labels` (`uuid`, `displayName`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'F56D7E8FA9B04C2D34EF5A6B7C8D9E0F', 'Italian', 1672531200, null, 'SYNCED'),
(X'A67E8F9AB0C14D3E45FA6B7C8D9E0F1A', 'Weeknight Dinner', 1672531200, null, 'SYNCED'),
(X'B78F9A0BC1D24E4F56AB7C8D9E0F1A2B', 'Healthy', 1672531200, null, 'SYNCED'),
(X'C89A0B1CD2E34F5A67BC8D9E0F1A2B3C', 'Seafood', 1672531200, null, 'SYNCED'),
(X'D90B1C2DE3F44A6B78CD9E0F1A2B3C4D', 'Mexican', 1672531200, null, 'SYNCED'),
(X'E01C2D3EF4A54B7C89DE0F1A2B3C4D5E', 'Comfort Food', 1672531200, null, 'SYNCED'),
(X'F12D3E4FA5B64C8D9AEF1A2B3C4D5E6F', 'Vegetarian', 1672531200, null, 'SYNCED'),
(X'A23E4F5AB6C74D9EAF0A2B3C4D5E6F7A', 'Vegan', 1672531200, null, 'SYNCED'),
(X'B34F5A6BC7D84EAFB01B3C4D5E6F7A8B', 'Gluten-Free', 1672531200, null, 'SYNCED'),
(X'C45A6B7CD8E94FBAC12C4D5E6F7A8B9C', 'Quick & Easy', 1672531200, null, 'SYNCED');

-- Tags
INSERT INTO `tags` (`uuid`, `displayName`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'D56B7C8DE9F04ACBD23D5E6F7A8B9C0D', 'pasta', 1672531200, null, 'SYNCED'),
(X'E67C8D9EF0A14BDBE34E6F7A8B9C0D1E', 'chicken', 1672531200, null, 'SYNCED'),
(X'F78D9E0FA1B24CECF45F7A8B9C0D1E2F', 'fish', 1672531200, null, 'SYNCED'),
(X'A89E0F1AB2C34DFDA56A8B9C0D1E2F3A', 'beef', 1672531200, null, 'SYNCED'),
(X'B90F1A2BC3D44EFCB67B9C0D1E2F3A4B', 'salad', 1672531200, null, 'SYNCED'),
(X'C01A2B3CD4E54F0DC78C0D1E2F3A4B5C', 'soup', 1672531200, null, 'SYNCED'),
(X'D12B3C4DE5F64A1ED89D1E2F3A4B5C6D', 'spicy', 1672531200, null, 'SYNCED'),
(X'E23C4D5EF6A74B2FE9AE2F3A4B5C6D7E', 'creamy', 1672531200, null, 'SYNCED'),
(X'F34D5E6FA7B84C3AFAFE3A4B5C6D7E8F', 'grilled', 1672531200, null, 'SYNCED'),
(X'A45E6F7AB8C94D4BAB0F4B5C6D7E8F9A', 'roasted', 1672531200, null, 'SYNCED');

-- Recipes
INSERT INTO `recipes` (`uuid`, `title`, `description`, `imageUrl`, `imageUrlThumbnail`, `prepTimeMinutes`, `cookTimeMinutes`, `servings`, `creatorId`, `recipeExternalUrl`, `privacy`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'11111111111111111111111111111111', 'Classic Spaghetti Carbonara', 'A traditional Italian pasta dish.', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 10, 20, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'22222222222222222222222222222222', 'Garlic Chicken and Broccoli', 'A simple and healthy one-pan meal.', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 15, 25, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'33333333333333333333333333333333', 'Lemon Dill Salmon', 'Flaky salmon with a fresh, citrusy sauce.', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 10, 15, 2, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', 'Hearty Beef Chilli', 'A warm and comforting bowl of chilli.', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 20, 60, 6, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'55555555555555555555555555555555', 'Avocado Toast with Egg', 'A simple and nutritious breakfast.', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 5, 5, 1, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'66666666666666666666666666666666', 'Pesto Pasta Salad', 'A refreshing pasta salad.', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 15, 10, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'77777777777777777777777777777777', 'Mushroom Risotto', 'Creamy and savory risotto.', 'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg', 'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg', 10, 40, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'88888888888888888888888888888888', 'Chicken Noodle Soup', 'Classic comfort soup.', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 15, 45, 6, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'99999999999999999999999999999999', 'Greek Salad', 'A fresh and tangy salad.', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 15, 0, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'Beef Tacos', 'Classic ground beef tacos.', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 20, 15, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'Lentil Soup', 'A hearty and healthy soup.', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 10, 50, 6, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'cccccccccccccccccccccccccccccccc', 'Sheet Pan Chicken Fajitas', 'An easy and flavorful weeknight meal.', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 15, 25, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'dddddddddddddddddddddddddddddddd', 'Caprese Salad', 'Simple and fresh tomato, mozzarella, and basil.', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 10, 0, 2, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', 'Garlic Shrimp Scampi', 'A quick and elegant pasta dish.', 'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg', 'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg', 10, 15, 2, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'ffffffffffffffffffffffffffffffff', 'Butternut Squash Soup', 'A creamy and comforting fall soup.', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg', 15, 40, 6, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'10101010101010101010101010101010', 'Black Bean Burgers', 'A delicious and satisfying veggie burger.', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg', 20, 15, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'12121212121212121212121212121212', 'Chicken Alfredo', 'A rich and creamy pasta classic.', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg', 10, 25, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'13131313131313131313131313131313', 'Roasted Root Vegetables', 'A simple and healthy side dish.', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg', 15, 45, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'14141414141414141414141414141414', 'Tomato Bruschetta', 'A classic Italian appetizer.', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg', 15, 5, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED'),
(X'15151515151515151515151515151515', 'Pad Thai', 'A classic Thai noodle dish.', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 'https://www.pressurecookrecipes.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg', 20, 15, 4, X'F47AC10B58CC4372A5670E02B2C3D479', null, 'PUBLIC', 1672531200, null, 'SYNCED');


-- Recipe Steps
INSERT INTO `recipe_steps` (`uuid`, `recipeId`, `orderIndex`, `instruction`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'DA8A7A8A5B8E4B6E8E5F2B8E3A3F5C7C', X'11111111111111111111111111111111', 1, 'Cook spaghetti according to package directions.', 1672531200, null, 'SYNCED'),
(X'E4B8D4E05B274D9F8C3E9B6E2D1C3A4F', X'11111111111111111111111111111111', 2, 'While pasta is cooking, fry guanciale until crisp.', 1672531200, null, 'SYNCED'),
(X'F3E9A4F23B6D4C8E9F2A1C8B4D6E2A1B', X'11111111111111111111111111111111', 3, 'In a bowl, whisk eggs, pecorino romano, and a generous amount of black pepper.', 1672531200, null, 'SYNCED'),
(X'04B3A2C18E9F4B7D6A5C4D3B2A1C8E9F', X'11111111111111111111111111111111', 4, 'Drain pasta, reserving some pasta water. Quickly mix hot pasta with the egg mixture and guanciale.', 1672531200, null, 'SYNCED'),
(X'15F2A1B09C8D4E6F7B4A3C2D1B8E9F7A', X'22222222222222222222222222222222', 1, 'Preheat oven to 400°F (200°C).', 1672531200, null, 'SYNCED'),
(X'26A1B2C3D4E54F6A8B9C0D1E2F3A4B5C', X'22222222222222222222222222222222', 2, 'Toss chicken and broccoli with olive oil, garlic, salt, and pepper.', 1672531200, null, 'SYNCED'),
(X'37B2C3D4E5F64A8B9C0D1E2F3A4B5C6D', X'22222222222222222222222222222222', 3, 'Arrange in a single layer on a baking sheet.', 1672531200, null, 'SYNCED'),
(X'48C3D4E5F6A74B9C8D1E2F3A4B5C6D7E', X'22222222222222222222222222222222', 4, 'Roast for 20-25 minutes, or until chicken is cooked through.', 1672531200, null, 'SYNCED');

-- Recipe Ingredients (Cross Ref)
INSERT INTO `recipe_ingredients` (`recipeId`, `ingredientId`, `quantity`, `unit`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'11111111111111111111111111111111', X'01C7B3E3802C4B6E8E5F2B8E3A3F5C7C', 400, 'g', 1672531200, null, 'SYNCED'),
(X'11111111111111111111111111111111', X'A2B8D4E05B274D9F8C3E9B6E2D1C3A4F', 150, 'g', 1672531200, null, 'SYNCED'),
(X'11111111111111111111111111111111', X'C3E9A4F23B6D4C8E9F2A1C8B4D6E2A1B', 4, 'large', 1672531200, null, 'SYNCED'),
(X'11111111111111111111111111111111', X'D4B3A2C18E9F4B7D6A5C4D3B2A1C8E9F', 50, 'g', 1672531200, null, 'SYNCED'),
(X'11111111111111111111111111111111', X'E5F2A1B09C8D4E6F7B4A3C2D1B8E9F7A', 1, 'tsp' , 1672531200, null, 'SYNCED'),
(X'22222222222222222222222222222222', X'F6A1B2C3D4E54F6A8B9C0D1E2F3A4B5C', 2, 'breasts', 1672531200, null, 'SYNCED'),
(X'22222222222222222222222222222222', X'17B2C3D4E5F64A8B9C0D1E2F3A4B5C6D', 1, 'head', 1672531200, null, 'SYNCED'),
(X'22222222222222222222222222222222', X'28C3D4E5F6A74B9C8D1E2F3A4B5C6D7E', 3, 'cloves', 1672531200, null, 'SYNCED'),
(X'22222222222222222222222222222222', X'39D4E5F6A7B84C0D9E2F3A4B5C6D7E8F', 2, 'tbsp', 1672531200, null, 'SYNCED'),
(X'33333333333333333333333333333333', X'4A5E6F7AB8C94D1E8F3A4B5C6D7E8F9A', 2, 'tbsp', 1672531200, null, 'SYNCED'),
(X'33333333333333333333333333333333', X'5B6F7A8BC9D04E2F9A4B5C6D7E8F9A0B', 1, 'tbsp', 1672531200, null, 'SYNCED'),
(X'33333333333333333333333333333333', X'6C7A8B9CD0E14F3AAB5C6D7E8F9A0B1C', 2, 'tbsp', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', X'7D8B9C0DE1F24A4BBC6D7E8F9A0B1C2D', 500, 'g', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', X'8E9C0D1EF2A34B5CCD7E8F9A0B1C2D3E', 1, 'large', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', X'9F0D1E2FA3B44C6DDE8F9A0B1C2D3E4F', 400, 'g', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', X'A01E2F3AB4C54D7EEF9A0B1C2D3E4F5A', 400, 'g', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', X'B12F3A4BC5D64E8FF0AB1C2D3E4F5A6B', 2, 'tbsp', 1672531200, null, 'SYNCED'),
(X'55555555555555555555555555555555', X'C23A4B5CD6E74F9A01BC2D3E4F5A6B7C', 1, 'large', 1672531200, null, 'SYNCED'),
(X'55555555555555555555555555555555', X'C3E9A4F23B6D4C8E9F2A1C8B4D6E2A1B', 1, 'large', 1672531200, null, 'SYNCED'),
(X'55555555555555555555555555555555', X'D34B5C6DE7F84A0B12CD3E4F5A6B7C8D', 1, 'tbsp', 1672531200, null, 'SYNCED'),
(X'55555555555555555555555555555555', X'E45C6D7EF8A94B1C23DE4F5A6B7C8D9E', 0.5, 'cups', 1672531200, null, 'SYNCED');

-- Recipe Labels (Cross Ref)
INSERT INTO `recipe_labels` (`recipeId`, `labelId`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'11111111111111111111111111111111', X'F56D7E8FA9B04C2D34EF5A6B7C8D9E0F', 1672531200, null, 'SYNCED'),
(X'11111111111111111111111111111111', X'A67E8F9AB0C14D3E45FA6B7C8D9E0F1A', 1672531200, null, 'SYNCED'),
(X'22222222222222222222222222222222', X'A67E8F9AB0C14D3E45FA6B7C8D9E0F1A', 1672531200, null, 'SYNCED'),
(X'22222222222222222222222222222222', X'B78F9A0BC1D24E4F56AB7C8D9E0F1A2B', 1672531200, null, 'SYNCED'),
(X'33333333333333333333333333333333', X'C89A0B1CD2E34F5A67BC8D9E0F1A2B3C', 1672531200, null, 'SYNCED'),
(X'33333333333333333333333333333333', X'C45A6B7CD8E94FBAC12C4D5E6F7A8B9C', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', X'D90B1C2DE3F44A6B78CD9E0F1A2B3C4D', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', X'E01C2D3EF4A54B7C89DE0F1A2B3C4D5E', 1672531200, null, 'SYNCED'),
(X'55555555555555555555555555555555', X'B78F9A0BC1D24E4F56AB7C8D9E0F1A2B', 1672531200, null, 'SYNCED'),
(X'55555555555555555555555555555555', X'C45A6B7CD8E94FBAC12C4D5E6F7A8B9C', 1672531200, null, 'SYNCED');

-- Recipe Tags (Cross Ref)
INSERT INTO `recipe_tags` (`recipeId`, `tagId`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'11111111111111111111111111111111', X'D56B7C8DE9F04ACBD23D5E6F7A8B9C0D', 1672531200, null, 'SYNCED'),
(X'11111111111111111111111111111111', X'E23C4D5EF6A74B2FE9AE2F3A4B5C6D7E', 1672531200, null, 'SYNCED'),
(X'22222222222222222222222222222222', X'E67C8D9EF0A14BDBE34E6F7A8B9C0D1E', 1672531200, null, 'SYNCED'),
(X'22222222222222222222222222222222', X'A45E6F7AB8C94D4BAB0F4B5C6D7E8F9A', 1672531200, null, 'SYNCED'),
(X'33333333333333333333333333333333', X'F78D9E0FA1B24CECF45F7A8B9C0D1E2F', 1672531200, null, 'SYNCED'),
(X'33333333333333333333333333333333', X'F34D5E6FA7B84C3AFAFE3A4B5C6D7E8F', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', X'A89E0F1AB2C34DFDA56A8B9C0D1E2F3A', 1672531200, null, 'SYNCED'),
(X'44444444444444444444444444444444', X'D12B3C4DE5F64A1ED89D1E2F3A4B5C6D', 1672531200, null, 'SYNCED'),
(X'55555555555555555555555555555555', X'B90F1A2BC3D44EFCB67B9C0D1E2F3A4B', 1672531200, null, 'SYNCED');


-- Allergens
INSERT INTO `allergens` (`uuid`, `displayName`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A', 'Gluten', 1672531200, null, 'SYNCED'),
(X'1B1B1B1B1B1B1B1B1B1B1B1B1B1B1B1B', 'Crustaceans', 1672531200, null, 'SYNCED'),
(X'1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C', 'Eggs', 1672531200, null, 'SYNCED'),
(X'1D1D1D1D1D1D1D1D1D1D1D1D1D1D1D1D', 'Fish', 1672531200, null, 'SYNCED'),
(X'1E1E1E1E1E1E1E1E1E1E1E1E1E1E1E1E', 'Peanuts', 1672531200, null, 'SYNCED'),
(X'1F1F1F1F1F1F1F1F1F1F1F1F1F1F1F1F', 'Soybeans', 1672531200, null, 'SYNCED'),
(X'2A2A2A2A2A2A2A2A2A2A2A2A2A2A2A2A', 'Milk', 1672531200, null, 'SYNCED'),
(X'2B2B2B2B2B2B2B2B2B2B2B2B2B2B2B2B', 'Nuts', 1672531200, null, 'SYNCED');


-- Source Classifications
INSERT INTO `source_classifications` (`uuid`, `category`, `subcategory`, `updatedAt`, `deletedAt`, `syncState`) VALUES
(X'3A3A3A3A3A3A3A3A3A3A3A3A3A3A3A3A', 'Produce', 'Fruit', 1672531200, null, 'SYNCED'),
(X'3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B', 'Produce', 'Vegetable', 1672531200, null, 'SYNCED'),
(X'3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C', 'Protein', 'Meat', 1672531200, null, 'SYNCED'),
(X'3D3D3D3D3D3D3D3D3D3D3D3D3D3D3D3D', 'Protein', 'Poultry', 1672531200, null, 'SYNCED'),
(X'3E3E3E3E3E3E3E3E3E3E3E3E3E3E3E3E', 'Protein', 'Seafood', 1672531200, null, 'SYNCED'),
(X'3F3F3F3F3F3F3F3F3F3F3F3F3F3F3F3F', 'Dairy', null, 1672531200, null, 'SYNCED'),
(X'4A4A4A4A4A4A4A4A4A4A4A4A4A4A4A4A', 'Pantry', 'Spices & Seasonings', 1672531200, null, 'SYNCED'),
(X'4B4B4B4B4B4B4B4B4B4B4B4B4B4B4B4B', 'Pantry', 'Oils & Fats', 1672531200, null, 'SYNCED'),
(X'4C4C4C4C4C4C4C4C4C4C4C4C4C4C4C4C', 'Pantry', 'Grains, Pasta & Legumes', 1672531200, null, 'SYNCED');

-- Update Ingredients with Allergens and Sources
UPDATE `ingredients` SET `allergenId` = X'1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A1A' WHERE `displayName` = 'Spaghetti';
UPDATE `ingredients` SET `sourcePrimaryId` = X'4C4C4C4C4C4C4C4C4C4C4C4C4C4C4C4C' WHERE `displayName` = 'Spaghetti';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C' WHERE `displayName` = 'Guanciale';
UPDATE `ingredients` SET `allergenId` = X'1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C' WHERE `displayName` = 'Eggs';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3D3D3D3D3D3D3D3D3D3D3D3D3D3D3D3D' WHERE `displayName` = 'Eggs';
UPDATE `ingredients` SET `allergenId` = X'2A2A2A2A2A2A2A2A2A2A2A2A2A2A2A2A' WHERE `displayName` = 'Pecorino Romano Cheese';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3F3F3F3F3F3F3F3F3F3F3F3F3F3F3F3F' WHERE `displayName` = 'Pecorino Romano Cheese';
UPDATE `ingredients` SET `sourcePrimaryId` = X'4A4A4A4A4A4A4A4A4A4A4A4A4A4A4A4A' WHERE `displayName` = 'Black Pepper';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3D3D3D3D3D3D3D3D3D3D3D3D3D3D3D3D' WHERE `displayName` = 'Chicken Breast';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B' WHERE `displayName` = 'Broccoli';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B' WHERE `displayName` = 'Garlic';
UPDATE `ingredients` SET `sourcePrimaryId` = X'4B4B4B4B4B4B4B4B4B4B4B4B4B4B4B4B' WHERE `displayName` = 'Olive Oil';
UPDATE `ingredients` SET `allergenId` = X'1D1D1D1D1D1D1D1D1D1D1D1D1D1D1D1D' WHERE `displayName` = 'Salmon Fillet';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3E3E3E3E3E3E3E3E3E3E3E3E3E3E3E3E' WHERE `displayName` = 'Salmon Fillet';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3A3A3A3A3A3A3A3A3A3A3A3A3A3A3A3A' WHERE `displayName` = 'Lemon';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B' WHERE `displayName` = 'Dill';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C3C' WHERE `displayName` = 'Beef Mince';
UPDATE `ingredients` SET `sourcePrimaryI d` = X'3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B' WHERE `displayName` = 'Onion';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B' WHERE `displayName` = 'Tomatoes';
UPDATE `ingredients` SET `sourcePrimaryId` = X'4C4C4C4C4C4C4C4C4C4C4C4C4C4C4C4C' WHERE `displayName` = 'Kidney Beans';
UPDATE `ingredients` SET `sourcePrimaryId` = X'4A4A4A4A4A4A4A4A4A4A4A4A4A4A4A4A' WHERE `displayName` = 'Chilli Powder';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3A3A3A3A3A3A3A3A3A3A3A3A3A3A3A3A' WHERE `displayName` = 'Avocado';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B3B' WHERE `displayName` = 'Coriander';
UPDATE `ingredients` SET `sourcePrimaryId` = X'3A3A3A3A3A3A3A3A3A3A3A3A3A3A3A3A' WHERE `displayName` = 'Lime';
